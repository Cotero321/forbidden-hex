"""Rebuild 18x18 brewing HUD sprites and merge their bilingual language entries.

Requires tools/requirements.txt. Run from any directory; --check is read-only.
Pixel motifs are authored at native resolution, with a one-pixel dark outline.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/forbidden_hex'
ENUM = ROOT / 'src/main/java/com/example/akaishi/forbidden/brewing/BrewingEffectType.java'

# id | Chinese | English | motif | Chinese description | English description
DATA = '''glowing|发光|Glowing|star|使目标轮廓发光。|Makes the target glow.
snow_trail|雪迹|Snow Trail|snow|在行进路径上留下积雪。|Leaves snow along the traveled path.
growth|生长|Growth|sprout|促进植物生长。|Encourages plant growth.
overgrowth|蔓生|Overgrowth|tree|使藤蔓附着目标，并增加目标受到的火焰伤害。|Covers the target in vines and increases fire damage taken.
dye|变色|Color Change|palette|改变目标颜色，包括可染色生物的毛色或项圈颜色。|Changes the target's color, including dyeable creatures' wool or collars.
extinguish|灭火|Extinguish|drop_cross|扑灭目标或周围的火焰。|Extinguishes flames on the target or nearby.
swim_speed|游泳加速|Swift Swimming|fish|提高游泳速度。|Increases swimming speed.
crush|粉碎|Crush|hammer|粉碎可受影响的方块。|Crushes susceptible blocks.
dispel|消散|Dispel|spark_cross|消散药水效果和区域效果云，包括龙息云。|Dispels potion effects and area effect clouds, including dragon breath clouds.
lava_repulsion|熔岩推斥|Lava Repulsion|lava|推斥附近的熔岩。|Repels nearby lava.
absorb_water|吸水|Absorb Water|sponge|吸收周围的水。|Absorbs surrounding water.
lingering_immunity|滞留免疫|Lingering Immunity|shield_cloud|抵御滞留药水效果云和龙息云的影响。|Protects against lingering potion clouds and dragon breath clouds.
drunk|醉酒|Drunkenness|cup|减少受到的伤害，并使饥饿耗竭增加50%。|Reduces incoming damage and increases hunger exhaustion by 50%.
nourish|滋养|Nourishment|food|为目标补充饱食。|Nourishes the target.
levitation|悬浮|Levitation|up|使目标向上悬浮。|Makes the target levitate upward.
water_walking|水上行走|Water Walking|boot_wave|使目标能在水面行走。|Allows walking on water.
luck|幸运|Luck|clover|提高目标的幸运。|Increases the target's luck.
jump_boost|跳跃提升|Jump Boost|boot_up|提高目标的跳跃高度。|Increases jump height.
slow_falling|缓降|Slow Falling|feather|减缓下落速度。|Slows falling speed.
web|蛛网|Web|web|用蛛网束缚目标。|Entangles the target in cobwebs.
thorns|尖刺|Spikes|thorn|周期性伤害附近1格内的目标。|Periodically damages targets within one block.
frost|冰霜|Frost|ice|冻结并减缓目标。|Freezes and slows the target.
smite|亡灵杀手|Smite|bolt|对亡灵目标造成魔法伤害。|Deals magic damage to undead targets.
reflection|反伤|Damage Reflection|mirror|将受到的部分伤害反还给攻击者。|Returns part of incoming damage to the attacker.
wither_plants|枯萎|Wither Plants|dead_tree|使可受影响的植物枯萎。|Withers susceptible plants.
poison_coating|抹毒|Poison Coating|blade_drop|使近战攻击附带中毒效果。|Applies poison through melee attacks.
launch|弹射|Launch|launch|将目标向上弹射。|Launches the target upward.
erosion|侵蚀|Erosion|cracked|损耗目标护甲耐久，并分解采掘等级低于铁镐的可受影响方块，不产生掉落。|Damages armor durability and disintegrates susceptible blocks below iron pickaxe mining tier without drops.
knockback|击飞|Knockback|fist_arrow|增强攻击的击退效果。|Increases attack knockback.
plague|瘟疫|Plague|germ|增加目标受到的伤害、降低移动速度，并向附近目标传播。|Increases damage taken, slows movement, and spreads to nearby targets.
twilight|黄昏|Twilight|moon|减少受到的伤害、提高攻击力，并在首次施加时恢复3点生命。|Reduces incoming damage, increases attack damage, and restores 3 health on first application.
absorption|伤害吸收|Absorption|heart_shield|提供额外的伤害吸收生命。|Grants additional absorption health.
random_teleport|随机传送|Random Teleport|portal|将目标传送到随机位置。|Teleports the target to a random location.
explosive|易爆|Explosive|bomb|使目标带有易爆效果。|Afflicts the target with an explosive effect.
speed|迅捷|Swiftness|wind|提高移动速度。|Increases movement speed.
slowness|迟缓|Slowness|snail|降低移动速度。|Decreases movement speed.
strength|力量|Strength|fist|提高近战攻击伤害。|Increases melee attack damage.
healing|瞬间治疗|Instant Health|heart_plus|立即恢复生命。|Instantly restores health.
harming|瞬间伤害|Instant Damage|heart_minus|立即造成伤害。|Instantly deals damage.
poison|中毒|Poison|skull|持续受到毒素伤害。|Deals poison damage over time.
regeneration|生命恢复|Regeneration|heart|持续恢复生命。|Restores health over time.
fire_resistance|抗火|Fire Resistance|shield_flame|抵御火焰与熔岩伤害。|Protects against fire and lava damage.
water_breathing|水下呼吸|Water Breathing|bubbles|允许在水下呼吸。|Allows breathing underwater.
night_vision|夜视|Night Vision|eye|提高黑暗中的视野。|Improves vision in darkness.
invisibility|隐身|Invisibility|ghost|使目标隐形。|Makes the target invisible.
weakness|虚弱|Weakness|broken_blade|降低近战攻击伤害。|Reduces melee attack damage.
turtle|神龟之力|Turtle Master|shell|提高抗性并降低移动速度。|Increases resistance while slowing movement.
blindness|失明|Blindness|eye_cross|限制目标的视野。|Restricts the target's vision.
scorch_trail|灼迹|Scorch Trail|boot_flame|在行进路径上留下灼烧痕迹。|Leaves a scorching trail along the traveled path.
bleach|褪色|Bleach|palette_cross|洗去可染色目标的颜色。|Removes color from dyeable targets.
ignite|引燃|Ignite|flame|点燃目标生物。|Sets the target creature on fire.
sinking|溺沉|Sinking|drop_down|降低游泳速度，并加快水下空气消耗。|Reduces swimming speed and accelerates underwater air loss.
condense|凝结|Condense|converge|将可受影响的物质凝结转化。|Condenses and transforms susceptible matter.
famine|饥馑|Famine|food_cross|消耗目标的饱食。|Depletes the target's nourishment.
heavy|沉重|Heaviness|weight|使目标行动沉重。|Weighs the target down.
fast_falling|急坠|Fast Falling|down|加快目标下落。|Accelerates the target's fall.
unluck|霉运|Bad Luck|clover_cross|降低目标的幸运。|Reduces the target's luck.
antidote|净毒|Antidote|drop_plus|解除中毒并在持续期间抵御中毒。|Removes poison and protects against it for the duration.
epidemic_guard|抑疫|Epidemic Guard|shield_germ|解除瘟疫并在持续期间抵御瘟疫。|Removes plague and protects against it for the duration.
pull|牵引|Pull|magnet|近战命中时将目标拉向攻击者。|Pulls the target toward the attacker on a melee hit.
shield_break|破盾|Shield Break|shield_crack|削减目标的伤害吸收生命。|Reduces the target's absorption health.
anchor|锚定|Anchor|anchor|阻止末影珍珠、紫颂果和随机传送，不限制正常移动。|Blocks ender pearl, chorus fruit, and random teleportation without restricting normal movement.
inert|惰化|Inertness|hourglass|解除易爆效果并减少爆炸伤害。|Removes the explosive effect and reduces explosion damage.
tenacity|坚韧|Tenacity|rooted_shield|提高击退抗性，减少受击位移。|Increases knockback resistance and reduces displacement from hits.
cold_resistance|御寒|Cold Resistance|warm_snow|阻止冻结与冰霜冻伤，减轻冰霜迟缓；不解除寒毒毒伤。|Prevents freezing and frost damage and reduces frost slowing; does not remove chilling poison damage.
sobriety|清醒|Sobriety|clear_drop|解除并抵御醉酒与昏睡。|Removes and prevents drunkenness and slumber.
mending|促愈|Mending|heart_plus|增强接受的生命治疗，不会自行回血或恢复理智。|Amplifies received health healing without restoring health or sanity on its own.
photosensitivity|光敏|Photosensitivity|sun_skin|暴露于直射日光时周期性受到灼伤，遮蔽或雨水可停止发作。|Causes periodic burns in direct sunlight; shelter or rain stops the reaction.
concealment|敛息|Concealment|veiled_breath|降低普通敌对生物的初次发现距离；造成攻击伤害后短暂失效，不取消已有追击。|Reduces initial detection range of ordinary hostiles; briefly disrupted by dealing attack damage and does not cancel pursuit.
viscosity|黏滞|Viscosity|sticky_boot|降低起跳高度和游泳速度。|Reduces jump height and swimming speed.
slumber|昏睡|Slumber|sleep_drop|普通生物先困倦后停止行动，受伤醒来；玩家仅短时禁止冲刺与跳跃。解除后短暂免疫。|Creatures become drowsy then stop moving until hurt; players briefly cannot sprint or jump. Removal grants brief immunity.
hardening|硬化|Hardening|stone_skin|提高护甲与护甲韧性，略微降低移动速度。|Increases armor and armor toughness at a small movement-speed cost.
blood_rush|血涌|Blood Rush|pulse_arrow|提高攻击速度，同时增加活动产生的饥饿耗竭。|Increases attack speed and exhaustion from activity.
lifesteal|汲血|Lifesteal|blood_fang|直接近战造成实际伤害时恢复少量生命，有单次上限和触发间隔。|Direct melee damage restores a limited amount of health with a trigger cooldown.
withering|虚耗|Vital Depletion|heart_minus|暂时降低最大生命，不直接致死；结束后恢复上限但不补血。|Temporarily reduces maximum health without directly killing; expiration restores the cap without healing.
drug_resistance|药抗|Drug Resistance|bottle_shield|缩短新施加的有害持续药效，不改变已有药效或瞬时伤害。|Shortens newly applied harmful lasting effects without changing existing effects or instant damage.
convulsions|惊厥|Convulsions|spasm|周期性短暂阻止冲刺与跳跃，保留转向、攻击和使用物品。|Periodically prevents sprinting and jumping briefly while preserving aiming, attacks and item use.
berserk|狂暴|Berserk|rage_fist|提高直接近战伤害，同时提高自身承伤。|Increases direct melee damage and damage received.
sedation|镇静|Sedation|calm_drop|普通生物暂时停止追击，受伤解除并短暂免疫；玩家与首领免疫。|Ordinary creatures stop pursuing until hurt, then gain brief immunity; players and bosses are immune.
hallucination|幻觉|Hallucination|double_eye|干扰视听，出现重影与错误方位声音；普通生物间歇丢失目标。|Distorts sight and hearing with double images and misleading sounds; ordinary creatures periodically lose their targets.
chilling_poison|寒毒|Chilling Poison|frost_drop|迟缓并周期性受到非致死毒伤；移动或冲刺加快毒性发作，不传播。|Slows and inflicts periodic nonlethal poison damage; movement or sprinting accelerates it without spreading.
strong_heart|强心|Strong Heart|heart_expand|暂时提高最大生命，新增生命空间需要治疗填满；结束后恢复原上限。|Temporarily increases maximum health without filling the added capacity; expiration restores the original cap.
bad_blood|坏血|Bad Blood|tainted_heart|降低接受的生命治疗与自然回血，但保留少量恢复能力。|Reduces received healing and natural regeneration while retaining limited recovery.
boiling_blood|沸血|Boiling Blood|heated_heart|直接近战命中点燃目标，持续增加饥饿耗竭。|Direct melee hits ignite targets while the effect continually increases exhaustion.
lucidity|明察|Lucidity|clear_eye|解除并抵御失明、黑暗与幻觉，在近距离无遮挡时辨认隐身生物。|Removes and prevents blindness, darkness and hallucinations and discerns nearby invisible creatures with clear line of sight.
cold_susceptibility|畏寒|Cold Susceptibility|cracked_snow|加快冻结积累并提高受到的冻伤。|Accelerates freezing buildup and increases freezing damage received.
drug_sensitivity|药敏|Drug Sensitivity|bottle_crack|延长新施加的有害持续药效，不影响已有药效或瞬时伤害。|Extends newly applied harmful lasting effects without changing existing effects or instant damage.'''
ENTRIES = {row[0]: row[1:] for line in DATA.splitlines() if (row := line.split('|'))}


def sprite(motif, color):
    mask = Image.new('RGBA', (18, 18))
    d = ImageDraw.Draw(mask)
    base = tuple(bytes.fromhex(color)) + (255,)
    light = tuple(min(255, c + 70) for c in base[:3]) + (255,)
    def line(points, fill=base, width=2): d.line(points, fill=fill, width=width)
    def poly(points, fill=base): d.polygon(points, fill=fill)
    def box(bounds, fill=base): d.rectangle(bounds, fill=fill)
    def oval(bounds, fill=base): d.ellipse(bounds, fill=fill)
    def cross(x=12, y=5):
        line([(x-2,y),(x+2,y)], light); line([(x,y-2),(x,y+2)], light)
    def shield():
        poly([(3,3),(9,1),(14,3),(13,10),(9,15),(4,11)])
        line([(5,4),(9,3),(12,4)], light, 1)
    def heart(): poly([(3,6),(5,3),(8,3),(9,5),(11,3),(14,3),(16,6),(15,10),(9,15),(3,10)])
    def drop():
        poly([(8,2),(3,10),(3,13),(6,15),(11,15),(14,12),(13,9)])
        line([(7,7),(5,11),(6,12)], light, 1)
    def flame():
        poly([(8,1),(10,7),(13,4),(15,10),(14,14),(10,16),(5,15),(3,11),(5,6),(6,10)])
        poly([(9,8),(11,12),(10,14),(7,13)], light)
    if motif in ('sprout','tree','dead_tree','thorn'):
        line([(9,15),(9,4)], base)
        if motif == 'dead_tree':
            line([(3,5),(5,8),(9,10),(13,7),(14,3)], base,1)
            line([(4,11),(8,12),(11,11)], base,1)
        elif motif == 'thorn':
            line([(3,14),(5,4),(8,8),(12,3),(14,14)], base)
            for x,y in [(4,8),(7,10),(12,7),(13,12)]: poly([(x,y),(x-2,y-3),(x+1,y-1)],light)
        else:
            poly([(8,10),(3,9),(2,5),(6,5),(9,8)])
            poly([(9,7),(11,2),(15,2),(14,6)])
            if motif == 'tree': poly([(9,12),(11,9),(16,8),(15,12)])
            line([(4,6),(7,8)],light,1)
    elif motif in ('snow','ice'):
        for a,b in [((9,2),(9,15)),((3,5),(15,12)),((3,12),(15,5))]: line([a,b])
        for x,y in [(9,4),(5,6),(13,6),(5,11),(13,11),(9,13)]:
            line([(x-1,y-1),(x,y),(x+1,y-1)],light,1)
        if motif == 'ice': box((6,6,11,11),light)
    elif motif.startswith('shield') or motif in ('heart_shield','shield_cloud','shield_germ'):
        shield()
        if motif == 'shield_crack': line([(10,3),(7,7),(11,8),(7,13)],(25,22,32,255),2)
        elif motif == 'shield_flame': poly([(9,5),(12,10),(9,12),(6,10)],light)
        elif motif == 'shield_germ':
            oval((6,6,11,11),light); box((8,4,9,13),light)
        elif motif == 'shield_cloud':
            oval((6,7,10,11),light); oval((9,6,13,11),light)
        else: poly([(6,7),(8,6),(9,8),(11,6),(12,8),(9,11)],light)
    elif motif.startswith('heart'):
        heart(); line([(5,5),(7,5)],light,1)
        if motif == 'heart_plus': cross(10,9)
        if motif == 'heart_minus': line([(6,9),(12,9)],light)
    elif motif in ('drop_cross','drop_down','drop_plus','bubbles','lava','sponge'):
        if motif == 'bubbles':
            for bounds in [(3,9,9,15),(10,4,14,8),(5,2,7,4)]: d.ellipse(bounds,outline=base,width=2)
        elif motif == 'sponge':
            box((3,4,14,14));
            for x,y in [(5,6),(11,6),(8,10),(12,12),(4,12)]: box((x,y,x+1,y+1),light)
        else:
            drop()
            if motif == 'drop_cross': line([(3,15),(15,3)],light)
            if motif == 'drop_plus': cross(12,6)
            if motif == 'drop_down': line([(9,7),(9,13),(6,10),(9,13),(12,10)],light,1)
            if motif == 'lava': line([(2,4),(5,2),(5,5)],light,1); line([(13,3),(16,5),(13,6)],light,1)
    elif motif in ('flame','boot_flame'):
        flame()
        if motif == 'boot_flame': box((2,12,11,15),light)
    elif motif in ('eye','eye_cross'):
        poly([(2,8),(6,4),(11,4),(16,8),(12,12),(6,12)])
        oval((6,5,11,11),light); box((8,6,9,10),(26,24,32,255))
        if motif == 'eye_cross': line([(3,14),(15,2)],light)
    elif motif in ('up','down','launch','boot_up','boot_wave','wind','fist_arrow'):
        if motif in ('down',): poly([(7,2),(10,2),(10,10),(14,10),(9,16),(3,10),(7,10)])
        elif motif in ('up','launch'): poly([(9,2),(15,8),(11,8),(11,15),(7,15),(7,8),(3,8)])
        elif motif == 'wind':
            for y,x in [(4,3),(8,1),(12,4)]: line([(x,y),(13,y),(15,y-1),(15,y-3)],base,1)
        elif motif == 'fist_arrow':
            box((2,6,8,12)); poly([(9,8),(12,8),(12,5),(16,9),(12,13),(12,10),(9,10)],light)
        else:
            poly([(5,3),(10,3),(10,10),(14,11),(14,14),(4,14),(3,12),(5,10)])
            if motif == 'boot_wave': line([(2,16),(5,15),(9,16),(13,15),(16,16)],light,1)
            else: line([(13,7),(13,2),(11,4),(13,2),(15,4)],light,1)
        if motif == 'launch': line([(3,16),(6,14),(11,14),(15,16)],light,1)
    elif motif in ('clover','clover_cross'):
        for bounds in [(3,3,8,8),(9,3,14,8),(3,9,8,14),(9,9,14,14)]: oval(bounds)
        line([(9,9),(10,16)],light,1)
        if motif.endswith('cross'): line([(3,15),(15,2)],light)
    elif motif in ('palette','palette_cross'):
        oval((2,3,15,14)); oval((10,10,15,15),(0,0,0,0))
        for bounds,c in [((4,5,6,7),(231,104,80,255)),((9,4,11,6),(105,183,219,255)),((12,7,14,9),(239,205,96,255))]: oval(bounds,c)
        if motif.endswith('cross'): line([(3,15),(15,2)],light)
    elif motif in ('food','food_cross'):
        poly([(3,11),(5,5),(13,2),(16,5),(14,12),(7,15),(3,14)])
        for x,y in [(6,7),(9,5),(10,10)]: line([(x,y),(x+2,y+2)],light,1)
        if motif.endswith('cross'): line([(2,15),(15,2)],light)
    elif motif in ('blade_drop','broken_blade','hammer'):
        if motif == 'hammer': box((3,3,14,7)); line([(8,7),(8,15)],light,3)
        else:
            poly([(12,2),(15,2),(15,5),(7,13),(4,10)])
            line([(3,9),(8,14)],light); line([(3,15),(6,12)],base)
            if motif == 'blade_drop': oval((12,11,15,15),light)
            else: line([(6,7),(12,10)],(0,0,0,0),3)
    elif motif in ('germ','skull'):
        oval((4,4,13,12)); box((6,10,11,14))
        for x in (6,10): box((x,7,x+1,8),(25,25,28,255))
        if motif == 'germ':
            for a,b in [((8,1),(8,4)),((1,8),(4,8)),((13,8),(16,8)),((8,13),(8,16)),((2,2),(5,5)),((12,12),(15,15))]: line([a,b],light,1)
    elif motif == 'web':
        for a,b in [((2,2),(15,15)),((2,15),(15,2)),((1,9),(16,9)),((9,1),(9,16))]: line([a,b],base,1)
        for lo,hi in [(5,12),(2,15)]: line([(9,lo),(hi,9),(9,hi),(lo,9),(9,lo)],light,1)
    elif motif == 'portal':
        d.ellipse((3,2,13,15),outline=base,width=3)
        line([(2,9),(15,9),(12,6),(15,9),(12,12)],light,1)
        box((14,2,15,3),light)
    elif motif == 'bomb':
        oval((3,6,14,16)); box((7,4,10,6)); line([(9,4),(11,2),(13,3)],light,1); cross(14,2)
        line([(5,9),(5,11)],light,1)
    elif motif == 'mirror':
        poly([(5,2),(13,2),(15,5),(15,12),(12,15),(4,15),(2,12),(2,5)])
        line([(5,12),(12,5)],light); line([(7,13),(13,7)],light,1)
        line([(1,7),(4,7),(2,5),(4,7),(2,9)],light,1)
    elif motif == 'moon':
        oval((3,2,15,14)); oval((8,1,16,10),(0,0,0,0)); cross(4,13)
    elif motif == 'fish':
        oval((2,6,12,12)); poly([(11,9),(16,5),(16,13)]); box((4,8,5,9),light)
        line([(3,14),(7,15),(11,14)],light,1)
    elif motif == 'feather':
        poly([(12,2),(15,3),(14,8),(8,14),(4,13),(4,9)])
        line([(3,16),(12,5)],light,1); line([(7,8),(10,8)],light,1)
    elif motif == 'cup':
        poly([(3,4),(12,4),(11,13),(5,13)]); d.rectangle((12,6,15,10),outline=light,width=1)
        line([(4,15),(12,15)],light,1); line([(6,1),(7,3)],light,1)
    elif motif == 'snail':
        oval((4,5,13,13)); line([(5,15),(15,15),(15,11)],light)
        line([(8,8),(11,8),(11,11),(8,11)],light,1)
    elif motif == 'fist':
        poly([(3,5),(5,5),(5,3),(8,3),(8,4),(13,4),(15,7),(14,12),(11,15),(6,15),(3,11)])
        for x in (6,9,12): line([(x,5),(x,8)],light,1)
    elif motif == 'shell':
        oval((3,3,14,14)); line([(8,4),(8,13)],light,1); line([(4,7),(13,7)],light,1); line([(4,11),(13,11)],light,1)
        for x in (2,14): box((x,5,x+1,7)); box((x,12,x+1,14))
    elif motif == 'ghost':
        d.arc((4,2,13,12),180,360,fill=base,width=2)
        line([(4,7),(4,15),(7,13),(9,15),(11,13),(13,15),(13,7)],base,1)
        box((6,7,7,8),light); box((10,7,11,8),light)
    elif motif == 'cracked':
        box((3,3,14,14)); line([(10,3),(7,7),(10,10),(6,14)],(24,22,28,255),2)
        box((1,13,2,15),light); box((15,4,16,6),light)
    elif motif == 'converge':
        box((7,7,10,10),light)
        for pts in [[(2,2),(6,6),(3,6)],[(15,2),(11,6),(11,3)],[(2,15),(6,11),(6,14)],[(15,15),(11,11),(14,11)]]: line(pts,base,1)
    elif motif == 'weight':
        d.ellipse((6,2,11,7),outline=light,width=2); poly([(5,6),(12,6),(15,15),(2,15)]); line([(6,9),(5,12)],light,1)
    elif motif == 'magnet':
        line([(4,3),(4,10),(6,14),(11,14),(14,10),(14,3)],base,3)
        box((3,2,5,5),light); box((13,2,15,5),light)
    elif motif == 'anchor':
        d.ellipse((6,1,11,6),outline=light,width=1)
        line([(9,5),(9,15),(4,13),(2,9)],base,2); line([(9,15),(14,13),(16,9)],base,2)
        line([(4,7),(13,7)],light,1)
    elif motif == 'hourglass':
        line([(4,2),(13,2),(13,5),(6,12),(6,14),(12,14),(12,12),(5,5),(5,2)],base,2)
        line([(4,16),(13,16)],light,1)
    elif motif in ('star','spark_cross','bolt'):
        if motif == 'bolt': poly([(10,1),(4,9),(8,9),(6,16),(15,6),(10,6)])
        else:
            poly([(9,1),(11,6),(16,8),(11,10),(9,16),(7,10),(1,8),(7,6)])
            if motif == 'spark_cross': line([(3,15),(15,2)],light)
    elif motif == 'rooted_shield':
        poly([(4,2),(13,2),(14,8),(9,13),(3,8)]); line([(9,6),(9,16),(5,15),(3,16)],light,1); line([(9,13),(14,16)],light,1)
    elif motif in ('warm_snow', 'cracked_snow'):
        line([(8,2),(8,14)],base); line([(3,5),(13,11)],base); line([(3,11),(13,5)],base)
        if motif == 'warm_snow': d.arc((1,1,16,16),285,100,fill=light,width=2)
        else: line([(11,1),(7,6),(10,9),(6,16)],light,1)
    elif motif in ('clear_drop', 'sleep_drop', 'calm_drop', 'frost_drop'):
        poly([(8,2),(13,8),(13,12),(10,15),(6,15),(3,12),(3,8)])
        if motif == 'clear_drop': line([(6,9),(8,12),(12,6)],light)
        elif motif == 'sleep_drop': line([(6,8),(11,8),(6,12),(11,12)],light,1); box((13,2,15,3),light)
        elif motif == 'calm_drop': line([(5,10),(7,11),(10,11),(12,10)],light,1); line([(6,7),(7,7)],light,1)
        else: line([(8,7),(8,13)],light,1); line([(5,8),(11,12)],light,1); line([(5,12),(11,8)],light,1)
    elif motif in ('heart_plus','heart_minus','heart_expand','tainted_heart','heated_heart'):
        poly([(2,5),(4,3),(7,3),(9,6),(11,3),(14,3),(16,5),(16,9),(9,15),(2,9)])
        if motif == 'heart_plus': line([(9,7),(9,12)],light,1); line([(6,9),(12,9)],light,1)
        elif motif == 'heart_minus': line([(5,9),(13,9)],light)
        elif motif == 'heart_expand': line([(1,12),(1,16),(5,16)],light,1); line([(12,1),(16,1),(16,4)],light,1)
        elif motif == 'tainted_heart': line([(10,4),(7,8),(10,11),(8,14)],light); box((4,6,5,7),light)
        else: line([(7,2),(6,0),(9,1),(10,0)],light,1); line([(6,8),(8,6),(9,10),(12,8)],light,1)
    elif motif == 'sun_skin':
        oval((2,2,8,8)); line([(1,10),(5,12),(4,15),(14,15),(15,8),(11,6)],base)
        for x,y in ((11,2),(13,4),(9,11)): box((x,y,x,y+1),light)
    elif motif == 'veiled_breath':
        poly([(3,3),(11,3),(14,13),(10,15),(3,13)]); line([(5,7),(8,6),(11,7)],light,1)
        line([(3,11),(6,10),(9,11),(15,9)],light,1); line([(6,9),(10,9)],light,1)
    elif motif == 'sticky_boot':
        poly([(5,2),(10,2),(10,10),(15,10),(15,14),(4,14)]); line([(1,16),(16,16)],light,1)
        for x in (4,8,12): line([(x,13),(x,16)],light,1)
    elif motif == 'stone_skin':
        poly([(5,2),(12,2),(12,5),(15,7),(14,15),(3,15),(2,7),(5,5)])
        line([(5,6),(9,8),(7,12),(11,15)],light,1); line([(9,8),(13,6)],light,1)
    elif motif == 'pulse_arrow':
        line([(1,10),(5,10),(7,5),(9,13),(11,8),(16,8)],base)
        line([(12,3),(16,3),(16,6)],light,1); line([(11,7),(16,2)],light,1)
    elif motif == 'blood_fang':
        poly([(3,2),(7,3),(5,11)]); poly([(11,3),(15,2),(13,11)])
        poly([(9,9),(12,13),(11,16),(7,16),(6,13)]); line([(8,13),(8,14)],light,1)
    elif motif in ('bottle_shield','bottle_crack'):
        box((6,1,11,3)); line([(6,4),(6,7),(3,10),(3,15),(14,15),(14,10),(11,7),(11,4)],base,1)
        if motif == 'bottle_shield': poly([(7,8),(12,8),(12,11),(9,14),(6,11)]); line([(8,10),(9,12),(11,9)],light,1)
        else: line([(10,5),(7,9),(10,11),(6,15)],light,1); box((14,3,15,5),light)
    elif motif == 'spasm':
        oval((7,1,10,4)); line([(8,5),(8,10),(4,15)],base); line([(8,10),(13,15)],base)
        line([(2,6),(5,7),(7,5),(12,8),(16,5)],light,1); line([(1,10),(3,12),(2,14)],light,1)
    elif motif == 'rage_fist':
        poly([(3,6),(5,6),(5,4),(8,4),(8,5),(13,5),(15,8),(13,14),(5,14),(3,11)])
        line([(6,6),(6,9),(9,9),(9,6)],light,1); line([(1,2),(4,4)],light,1); line([(13,1),(12,4)],light,1)
    elif motif == 'double_eye':
        line([(1,6),(4,3),(9,3),(12,6),(9,9),(4,9),(1,6)],base,1)
        line([(5,11),(8,8),(13,8),(16,11),(13,14),(8,14),(5,11)],light,1); oval((5,5,7,7)); oval((10,10,12,12),light)
    elif motif == 'clear_eye':
        line([(1,9),(5,5),(12,5),(16,9),(12,13),(5,13),(1,9)],base)
        oval((7,7,10,11),light); line([(3,1),(3,4)],light,1); line([(1,2),(5,2)],light,1); line([(13,15),(16,15)],light,1)
    else:
        raise ValueError(f'Unknown motif: {motif}')
    # Outline only occupied pixels; preserve transparent holes and native crisp edges.
    result = Image.new('RGBA', mask.size)
    alpha = mask.getchannel('A')
    pixels = alpha.load()
    out = result.load()
    for y in range(18):
        for x in range(18):
            if not pixels[x,y] and any(0 <= x+dx < 18 and 0 <= y+dy < 18 and pixels[x+dx,y+dy] for dx,dy in ((-1,0),(1,0),(0,-1),(0,1))):
                out[x,y] = (25,23,30,240)
    result.alpha_composite(mask)
    return result


UI = {
    'tooltip.forbidden_hex.brewing.fume_effect': ('药性：%s · 消耗效果容量 %s', 'Effect: %s · Effect capacity cost: %s'),
    'tooltip.forbidden_hex.brewing.custom_effect': ('%s %s — %s', '%s %s — %s'),
    'tooltip.forbidden_hex.brewing.effect_cost': ('效果容量消耗：%s', 'Effect capacity cost: %s'),
    'message.forbidden_hex.brewing.custom_budget': ('效果容量不足：需要 %s，当前 %s。', 'Insufficient effect capacity: requires %s, available %s.'),
    'message.forbidden_hex.brewing.custom_incomplete': ('自定义药效配方尚未完成。', 'The custom effect recipe is incomplete.'),
}


def run(check):
    declared = re.findall(r'^\s*([A-Z_]+)\(\d+,\s*\d+,\s*0x([0-9A-Fa-f]+)\)', ENUM.read_text(encoding='utf-8'), re.M)
    assert {key.lower() for key, _ in declared} == set(ENTRIES), 'Enum and authored resource definitions differ'
    destination = ASSETS / 'textures/mob_effect'
    if not check: destination.mkdir(parents=True, exist_ok=True)
    hashes = set()
    for key, color in declared:
        name = key.lower()
        image = sprite(ENTRIES[name][2], color)
        path = destination / f'brewing_{name}.png'
        if check:
            with Image.open(path) as actual:
                assert actual.size == (18,18) and actual.mode == 'RGBA', str(path)
                assert actual.tobytes() == image.tobytes(), f'Outdated sprite: {path}'
        else: image.save(path, optimize=False)
        hashes.add(hashlib.sha256(image.tobytes()).digest())
    assert len(hashes) == len(declared), 'Duplicate sprite artwork'
    for index, locale in enumerate(('zh_cn','en_us')):
        path = ASSETS / 'lang' / f'{locale}.json'
        # Retain unrelated text and formatting; only update this tool's owned entries.
        text = path.read_text(encoding='utf-8')
        existing = json.loads(text)
        expected = {key: values[index] for key, values in UI.items()}
        for name, values in ENTRIES.items():
            key = f'effect.forbidden_hex.brewing_{name}'
            expected[key] = values[index]
            expected[key + '.description'] = values[3 + index]
        if check:
            assert all(existing.get(key) == value for key,value in expected.items()), f'Outdated translations: {locale}'
        else:
            additions = []
            for key,value in expected.items():
                entry = json.dumps(key,ensure_ascii=False) + ': ' + json.dumps(value,ensure_ascii=False)
                if key in existing:
                    pattern = r'"' + re.escape(key) + r'"\s*:\s*"(?:\\.|[^"\\])*"'
                    text, count = re.subn(pattern, lambda _: entry, text)
                    assert count == 1, f'Duplicate language key: {key}'
                else: additions.append(entry)
            if additions:
                end = text.rfind('}')
                text = text[:end].rstrip() + ',\n' + ',\n'.join(additions) + '\n}\n'
            json.loads(text)
            path.write_text(text, encoding='utf-8')
    print(f'{"Verified" if check else "Generated"} {len(declared)} unique 18x18 RGBA icons; {len(ENTRIES)*2+len(UI)} language keys per locale.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Check resources without writing files')
    run(parser.parse_args().check)
