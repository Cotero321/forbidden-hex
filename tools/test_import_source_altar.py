import math
import unittest

from import_source_altar import area, clip, corners, obj_text, rotate, transform_tree


class SourceAltarImportTests(unittest.TestCase):
    def test_xyz_rotation_matches_three_matrix(self):
        point, pivot, angles = [7, -3, 11], [2, 4, -1], [5, 60, 45]
        a, b = math.cos(math.radians(angles[0])), math.sin(math.radians(angles[0]))
        c, d = math.cos(math.radians(angles[1])), math.sin(math.radians(angles[1]))
        e, f = math.cos(math.radians(angles[2])), math.sin(math.radians(angles[2]))
        matrix = [[c * e, -c * f, d], [a * f + b * e * d, a * e - b * f * d, -b * c], [b * f - a * e * d, b * e + a * f * d, a * c]]
        expected = [sum(matrix[i][j] * (point[j] - pivot[j]) for j in range(3)) + pivot[i] for i in range(3)]
        for actual, reference in zip(rotate(point, pivot, angles), expected):
            self.assertAlmostEqual(actual, reference, places=12)

    def test_face_winding_points_outward(self):
        element = {"from": [0, 0, 0], "to": [1, 1, 1]}
        normals = {"north": [0, 0, -1], "south": [0, 0, 1], "east": [1, 0, 0], "west": [-1, 0, 0], "up": [0, 1, 0], "down": [0, -1, 0]}
        for face, normal in normals.items():
            points = corners(element, face)
            a = [points[1][i] - points[0][i] for i in range(3)]
            b = [points[2][i] - points[0][i] for i in range(3)]
            cross = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
            self.assertEqual(cross, normal, face)

    def test_clip_conserves_area_and_interpolates_uv(self):
        face = [[0, 0, 0, 0, 0], [0, 0, 32, 0, 1], [48, 0, 32, 1, 1], [48, 0, 0, 1, 0]]
        total = 0
        for x in [0, 16, 32]:
            for z in [0, 16]:
                fragment = face
                for axis, boundary, positive in [(0, x, True), (0, x + 16, False), (2, z, True), (2, z + 16, False)]:
                    fragment = clip(fragment, axis, boundary, positive)
                total += area(fragment)
                for vertex in fragment:
                    self.assertAlmostEqual(vertex[3], vertex[0] / 48)
                    self.assertAlmostEqual(vertex[4], vertex[2] / 32)
        self.assertAlmostEqual(total, area(face))

    def test_nested_group_order(self):
        model = {"elements": [{"uuid": "cube"}], "groups": [{"uuid": "outer"}, {"uuid": "inner"}], "outliner": [{"uuid": "outer", "children": [{"uuid": "inner", "children": ["cube"]}]}]}
        self.assertEqual([g["uuid"] for g in transform_tree(model)["cube"]], ["inner", "outer"])
        model["outliner"].append("cube")
        with self.assertRaises(ValueError):
            transform_tree(model)

    def test_triangulation_preserves_polygon_area(self):
        polygon = [[0, 0, 0, 0, 0], [0, 0, 1, 0, 1], [1, 0, 1, 1, 1], [1, 0, 0, 1, 0]]
        text, count = obj_text([(0, polygon)])
        self.assertEqual(count, 2)
        self.assertEqual(len([line for line in text.splitlines() if line.startswith("f ")]), 2)
        self.assertAlmostEqual(area(polygon), area(polygon[:3]) + area([polygon[0], polygon[2], polygon[3]]))


if __name__ == "__main__":
    unittest.main()
