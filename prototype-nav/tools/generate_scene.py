"""Original, deterministic low-poly demo world. No external models, textures or map data."""
import json, math, pathlib, random, struct
ROOT = pathlib.Path(__file__).resolve().parents[1]
materials = []
def material(name, color, roughness=.8, unlit=False):
    m = {'name': name, 'pbrMetallicRoughness': {'baseColorFactor': color, 'metallicFactor': 0, 'roughnessFactor': roughness}}
    if unlit: m['extensions'] = {'KHR_materials_unlit': {}}
    materials.append(m)
    return len(materials)-1
sand=material('Desert dusk',[.28,.19,.16,1]); road=material('Asphalt',[.085,.095,.115,1]); white=material('Lane markings',[.78,.77,.65,1],unlit=True)
blue=material('Route blue',[.015,.38,1,1],unlit=True); red=material('Truck red',[.65,.022,.035,1],.28)
black=material('Black trim',[.022,.025,.032,1]); glass=material('Cabin glass',[.07,.14,.20,1],.16)
steel=material('Wheel metal',[.26,.29,.33,1],.3); lamp=material('Tail lamps',[1,.025,.012,1],unlit=True)
rock=material('Mountains',[.20,.17,.20,1]); green=material('Desert shrubs',[.12,.15,.095,1])
groups={}
def tri(node, mat, a,b,c):
    vs,ns=groups.setdefault((node,mat),([],[]))
    u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
    n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
    l=math.sqrt(sum(x*x for x in n)) or 1
    vs.extend([a,b,c]);ns.extend([[x/l for x in n]]*3)
def quad(node,mat,a,b,c,d): tri(node,mat,a,b,c);tri(node,mat,a,c,d)
def box(node,mat,x,y,z,w,h,d):
    p=[(x+i*w/2,y+j*h/2,z+k*d/2) for i,j,k in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
    for ids in [(0,3,2,1),(4,5,6,7),(0,4,7,3),(1,2,6,5),(3,7,6,2),(0,1,5,4)]:quad(node,mat,*[p[i] for i in ids])
def cone(node,mat,x,y,z,r,h,steps=7):
    for i in range(steps):
        a=2*math.pi*i/steps;b=2*math.pi*(i+1)/steps
        tri(node,mat,(x+r*math.cos(b),y,z+r*math.sin(b)),(x+r*math.cos(a),y,z+r*math.sin(a)),(x,y+h,z))
def route(s):
    t=max(0,min(1,(s-200)/160))
    return 80*(t*t*(3-2*t)), -s

def strip(s0,s1,left,right,mat,y=.025,curved=True):
    x0,z0=route(s0) if curved else (0,-s0);x1,z1=route(s1) if curved else (0,-s1)
    quad('World',mat,(x0+left,y,z0),(x0+right,y,z0),(x1+right,y,z1),(x1+left,y,z1))
box('World',sand,0,-1,-280,2000,1.9,1700)
for s in range(-100,650,5):
    strip(s,s+5,-9,9,road,0,False)
    strip(s,s+5,-8.3,-8.15,white,.015,False);strip(s,s+5,8.15,8.3,white,.015,False)
    if s%15==0:strip(s,s+5,-.06,.06,white,.018,False)
    if s>=195:
        strip(s,s+5,-4.5,4.5,road,.035)
        strip(s,s+5,-4.2,-4.08,white,.05);strip(s,s+5,4.08,4.2,white,.05)
    strip(s,s+5,-.65,.65,blue,.07)
rng=random.Random(72)
for i in range(100):
    s=rng.uniform(-70,680);x,z=route(s);side=rng.choice([-1,1]);x+=side*rng.uniform(22,110)
    cone('World',green,x,0,z,rng.uniform(.6,1.6),rng.uniform(.8,2.5))
for i in range(50):
    s=rng.uniform(-200,900);x,z=route(s);x+=rng.choice([-1,1])*rng.uniform(170,650)
    cone('World',rock,x,-2,z,rng.uniform(50,130),rng.uniform(45,140),rng.randint(5,8))
# Original pickup proxy. Front points toward negative Z. All geometry belongs to one movable node.
box('Truck',black,0,.57,0,2.05,.3,4.8)
box('Truck',red,0,1.02,0,2.02,.64,4.85)
box('Truck',red,0,1.52,-1.6,2.0,.36,1.5)
box('Truck',glass,0,1.98,-.35,1.78,.75,1.65)
box('Truck',red,0,2.4,-.35,1.97,.12,1.95)
for x in [-.94,.94]:
    box('Truck',red,x,1.75,.64,.13,1.22,.16)
    box('Truck',red,x,1.75,-1.19,.13,1.15,.13)
    box('Truck',red,x,1.44,1.6,.16,.47,1.6)
    # Black RamBar-like sports bar behind cab, an original simple shape.
    box('Truck',black,x*.83,2.03,.83,.12,1.14,.12)
box('Truck',black,0,2.6,.83,1.69,.15,.15)
box('Truck',black,0,1.33,1.53,1.73,.08,1.45)
box('Truck',red,0,1.45,2.35,1.99,.49,.15)
box('Truck',black,0,.84,2.51,2.08,.19,.18)
box('Truck',steel,0,1.47,2.436,.58,.09,.01)
for x in [-.85,.85]:box('Truck',lamp,x,1.48,2.44,.18,.29,.03)
for x in [-1.03,1.03]:
    for z in [-1.57,1.52]:
        # Cylinder around X, with outward-facing tread and side walls.
        steps=16;r=.58;half=.22
        for i in range(steps):
            a=2*math.pi*i/steps;b=2*math.pi*(i+1)/steps
            p=lambda xx,t:(xx,.62+r*math.cos(t),z+r*math.sin(t))
            quad('Truck',black,p(x-half,a),p(x-half,b),p(x+half,b),p(x+half,a))
            tri('Truck',black,(x+half,.62,z),p(x+half,a),p(x+half,b))
            tri('Truck',black,(x-half,.62,z),p(x-half,b),p(x-half,a))
        box('Truck',steel,x+(.23 if x>0 else -.23),.62,z,.025,.4,.4)
# glTF, non-indexed triangles, flat normals; compact binary and no proprietary assets.
binary=bytearray();views=[];accessors=[];meshes=[];nodes=[]
def accessor(values):
    offset=len(binary)
    for v in values:binary.extend(struct.pack('<3f',*v))
    views.append({'buffer':0,'byteOffset':offset,'byteLength':len(binary)-offset,'target':34962})
    accessors.append({'bufferView':len(views)-1,'componentType':5126,'count':len(values),'type':'VEC3','min':[min(v[i] for v in values) for i in range(3)],'max':[max(v[i] for v in values) for i in range(3)]})
    return len(accessors)-1
for name in ['World','Truck']:
    primitives=[]
    for (node,mat),(vs,ns) in groups.items():
        if node==name:primitives.append({'attributes':{'POSITION':accessor(vs),'NORMAL':accessor(ns)},'material':mat})
    meshes.append({'name':name,'primitives':primitives});nodes.append({'name':name,'mesh':len(meshes)-1})
doc={'asset':{'version':'2.0','generator':'TRX APEX original synthetic prototype'},'extensionsUsed':['KHR_materials_unlit'],'scene':0,'scenes':[{'nodes':[0,1]}],'nodes':nodes,'meshes':meshes,'materials':materials,'buffers':[{'byteLength':len(binary)}],'bufferViews':views,'accessors':accessors}
js=json.dumps(doc,separators=(',',':')).encode();js+=b' '*((-len(js))%4)
binary+=b'\x00'*((-len(binary))%4)
output=struct.pack('<III',0x46546c67,2,12+8+len(js)+8+len(binary))+struct.pack('<II',len(js),0x4e4f534a)+js+struct.pack('<II',len(binary),0x004e4942)+binary
path=ROOT/'app/src/main/assets/scene.glb';path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(output)
assert all(math.isfinite(v) for vs,ns in groups.values() for arr in (vs,ns) for vertex in arr for v in vertex)
assert all(a['count']%3==0 for a in accessors)
assert route(0)==(0,0) and route(360)==(80,-360)
print(f'{path}: {len(output):,} bytes; {sum(len(vs)//3 for vs,ns in groups.values()):,} triangles; 2 nodes; synthetic geometry validated')
