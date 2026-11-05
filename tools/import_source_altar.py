"""Import the supplied static Blockbench altar as six Forge OBJ block models."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/forbidden_hex"
EPSILON = 1e-9


def rotate(point, pivot, angles):
    x, y, z = (point[i] - pivot[i] for i in range(3))
    ax, ay, az = (math.radians(a) for a in angles)
    # THREE.Euler XYZ: Rz is applied first to a column vector.
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    return [x + pivot[0], y + pivot[1], z + pivot[2]]


def corners(element, face):
    x1, y1, z1 = element["from"]
    x2, y2, z2 = element["to"]
    return {
        "north": [(x2, y2, z1), (x2, y1, z1), (x1, y1, z1), (x1, y2, z1)],
        "south": [(x1, y2, z2), (x1, y1, z2), (x2, y1, z2), (x2, y2, z2)],
        "east": [(x2, y2, z2), (x2, y1, z2), (x2, y1, z1), (x2, y2, z1)],
        "west": [(x1, y2, z1), (x1, y1, z1), (x1, y1, z2), (x1, y2, z2)],
        "up": [(x1, y2, z1), (x1, y2, z2), (x2, y2, z2), (x2, y2, z1)],
        "down": [(x1, y1, z2), (x1, y1, z1), (x2, y1, z1), (x2, y1, z2)],
    }[face]


def area(polygon):
    result = 0.0
    for i in range(1, len(polygon) - 1):
        a = [polygon[i][j] - polygon[0][j] for j in range(3)]
        b = [polygon[i + 1][j] - polygon[0][j] for j in range(3)]
        cross = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
        result += math.sqrt(sum(v * v for v in cross)) / 2
    return result


def clip(polygon, axis, boundary, positive):
    if not polygon:
        return []
    result = []
    previous = polygon[-1]
    for current in polygon:
        a, b = previous[axis] - boundary, current[axis] - boundary
        inside_a = a >= -EPSILON if positive else a <= EPSILON
        inside_b = b >= -EPSILON if positive else b <= EPSILON
        if inside_a != inside_b:
            t = a / (a - b)
            vertex = [previous[j] + t * (current[j] - previous[j]) for j in range(5)]
            vertex[axis] = boundary
            result.append(vertex)
        if inside_b:
            result.append(current)
        previous = current
    clean = []
    for point in result:
        if not clean or any(abs(point[j] - clean[-1][j]) > EPSILON for j in range(3)):
            clean.append(point)
    if len(clean) > 1 and all(abs(clean[0][j] - clean[-1][j]) < EPSILON for j in range(3)):
        clean.pop()
    return clean


def transform_tree(model):
    groups = {g["uuid"]: g for g in model.get("groups", [])}
    elements = {e["uuid"]: e for e in model["elements"]}
    transforms = {}

    def visit(nodes, ancestors):
        for node in nodes:
            if isinstance(node, str):
                if node in transforms:
                    raise ValueError("Duplicate element in outliner")
                transforms[node] = ancestors
            else:
                group = groups[node["uuid"]]
                visit(node.get("children", []), [group] + ancestors)

    visit(model["outliner"], [])
    if set(transforms) != set(elements):
        raise ValueError("Outliner does not cover all elements")
    return transforms


def convert(model):
    if model.get("animations"):
        raise ValueError("This importer only supports static models")
    transforms = transform_tree(model)
    parts = [[] for _ in range(6)]
    source_area = 0.0
    split_area = 0.0
    for element in model["elements"]:
        if element["type"] != "cube" or element.get("inflate", 0):
            raise ValueError("Unsupported element type or inflation")
        if any(element["to"][i] <= element["from"][i] for i in range(3)):
            raise ValueError("Nonpositive cube dimensions")
        for direction, face in element["faces"].items():
            if face.get("texture") is None:
                continue
            texture_index = face["texture"]
            texture = model["textures"][texture_index]
            width, height = texture["uv_width"], texture["uv_height"]
            u1, v1, u2, v2 = face["uv"]
            if not (0 <= min(u1, u2) <= max(u1, u2) <= width and 0 <= min(v1, v2) <= max(v1, v2) <= height):
                raise ValueError("UV out of texture bounds")
            uvs = [(u1 / width, v1 / height), (u1 / width, v2 / height), (u2 / width, v2 / height), (u2 / width, v1 / height)]
            turns = face.get("rotation", 0) // 90
            uvs = uvs[turns:] + uvs[:turns] if turns else uvs
            polygon = []
            for point, uv in zip(corners(element, direction), uvs):
                point = rotate(point, element.get("origin", [0, 0, 0]), element.get("rotation", [0, 0, 0]))
                for group in transforms[element["uuid"]]:
                    point = rotate(point, group["origin"], group.get("rotation", [0, 0, 0]))
                # Uniform 1.5x scale: a 48x24 base centered in the existing 48x32 footprint.
                point = [(point[0] + 8) * 1.5, point[1] * 1.5, (point[2] + 8) * 1.5 + 4]
                polygon.append(point + list(uv))
            original = area(polygon)
            source_area += original
            face_area = 0.0
            for part in range(6):
                x, z = part % 3 * 16, part // 3 * 16
                if any(p[0] < -EPSILON or p[0] > 48 + EPSILON or p[2] < -EPSILON or p[2] > 32 + EPSILON for p in polygon):
                    raise ValueError("Geometry exceeds the existing 3x2 footprint")
                # Assign a coplanar internal boundary face to exactly one cell.
                if (x > 0 and all(abs(p[0] - x) < EPSILON for p in polygon)) or (z > 0 and all(abs(p[2] - z) < EPSILON for p in polygon)):
                    continue
                clipped = polygon
                for axis, bound, positive in [(0, x, True), (0, x + 16, False), (2, z, True), (2, z + 16, False)]:
                    clipped = clip(clipped, axis, bound, positive)
                fragment_area = area(clipped)
                if fragment_area <= EPSILON:
                    continue
                face_area += fragment_area
                vertices = [[(p[0] - x) / 16, p[1] / 16, (p[2] - z) / 16, p[3], p[4]] for p in clipped]
                parts[part].append((texture_index, vertices))
            if not math.isclose(original, face_area, rel_tol=1e-8, abs_tol=1e-7):
                raise ValueError(f"Clipping lost or duplicated surface area: {original} != {face_area}")
            split_area += face_area
    if not all(parts):
        raise ValueError("Empty formed part")
    return parts, source_area, split_area


def obj_text(faces):
    lines = ["mtllib forbidden_hex_source_altar_imported.mtl", "g altar"]
    index = 1
    triangles = 0
    for texture, polygon in faces:
        lines.append(f"usemtl texture_{texture}")
        for vertex in polygon:
            lines.append("v " + " ".join(f"{x:.10f}" for x in vertex[:3]))
            lines.append("vt " + " ".join(f"{x:.10f}" for x in vertex[3:]))
        for i in range(1, len(polygon) - 1):
            lines.append("f " + " ".join(f"{j}/{j}" for j in [index, index + i, index + i + 1]))
            triangles += 1
        index += len(polygon)
    return "\n".join(lines) + "\n", triangles


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    with zipfile.ZipFile(args.archive) as archive:
        if archive.testzip() is not None:
            raise ValueError("Archive CRC failed")
        model_names = [n for n in archive.namelist() if n.endswith(".bbmodel")]
        if len(model_names) != 1:
            raise ValueError("Expected exactly one Blockbench model")
        model = json.loads(archive.read(model_names[0]))
        parts, source_area, split_area = convert(model)
        textures = []
        for texture in model["textures"]:
            data = archive.read(texture["name"])
            if data[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", data[16:24]) != (64, 64):
                raise ValueError("Expected supplied 64x64 PNG texture")
            textures.append((texture["name"], data))
    destination = ASSETS / "models/block"
    destination.mkdir(parents=True, exist_ok=True)
    materials = []
    for index, (name, data) in enumerate(textures):
        texture_name = "forbidden_hex_source_altar_imported_" + Path(name).stem
        path = ASSETS / "textures/block" / (texture_name + ".png")
        path.write_bytes(data)
        materials.extend([f"newmtl texture_{index}", "Kd 1 1 1", "Ka 0 0 0", f"map_Kd forbidden_hex:block/{texture_name}", ""])
        print(f"Texture {name}: SHA256 {hashlib.sha256(data).hexdigest()}")
    (destination / "forbidden_hex_source_altar_imported.mtl").write_text("\n".join(materials), encoding="utf-8")
    for part, faces in enumerate(parts):
        text, triangles = obj_text(faces)
        (destination / f"forbidden_hex_source_altar_imported_formed_{part}.obj").write_text(text, encoding="utf-8")
        wrapper = {
            "parent": "minecraft:block/block", "loader": "forge:obj",
            "model": f"forbidden_hex:models/block/forbidden_hex_source_altar_imported_formed_{part}.obj",
            "automatic_culling": False, "flip_v": False, "emissive_ambient": False,
            "render_type": "minecraft:cutout", "ambientocclusion": False,
            "textures": {"particle": "forbidden_hex:block/forbidden_hex_source_altar_imported_bottom"},
        }
        (destination / f"forbidden_hex_source_altar_imported_formed_{part}.json").write_text(json.dumps(wrapper, indent=2) + "\n", encoding="utf-8")
        print(f"Part {part}: {len(faces)} clipped faces, {triangles} triangles")
    print(f"Surface area conserved: {source_area:.9f} == {split_area:.9f}")


if __name__ == "__main__":
    main()
