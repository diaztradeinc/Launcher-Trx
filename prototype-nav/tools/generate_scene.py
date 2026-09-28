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
rock=material('Mountains',[.15,.12,.16,1]); green=material('Desert shrubs',[.085,.105,.065,1])
shoulder=material('Gravel shoulders',[.19,.14,.115,1])
cliffs=[material('Sandstone '+str(i),[.18+i*.013,.115+i*.009,.09+i*.007,1]) for i in range(6)]
chrome=material('Brushed metal',[.4,.42,.45,1],.24)
headlamp=material('White LED',[.7,.82,1,1],unlit=True)
route_edge=material('Route edge',[.01,.1,.38,1],unlit=True)
chevron=material('Route chevrons',[.32,.8,1,1],unlit=True)
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
# Continuous ridgelines with a low road corridor, rather than isolated pyramid hills.
peaks=[(rng.uniform(-700,700),rng.uniform(-950,300),rng.uniform(18,55),rng.uniform(65,130)) for _ in range(44)]
def height(x,z):
    clearance=min(abs(x),abs(x-route(-z)[0]))
    fade=max(0,min(1,(clearance-65)/135))
    return -.12+fade*sum(h*math.exp(-((x-px)**2+(z-pz)**2)/(2*r*r)) for px,pz,h,r in peaks)
for x in range(-700,700,28):
    for z in range(-850,300,28):
        pts=[(xx,height(xx,zz),zz) for xx,zz in [(x,z),(x+28,z),(x+28,z-28),(x,z-28)]]
        mat=cliffs[int((sum(v[1] for v in pts)/4)/14)%6]
        quad('World',mat,*pts)
# Shoulders, guard rails and reflectors provide depth and scale.
for ss in range(-100,640,5):
    for side in [-1,1]:
        strip(ss,ss+5,side*9,side*10.3,shoulder,-.02,False) if side>0 else strip(ss,ss+5,-10.3,-9,shoulder,-.02,False)
    if ss%10==0:
        for side in [-1,1]:
            xx,zz=route(ss);xx+=side*(10.5 if ss<195 else 5.5)
            box('World',steel,xx,.6,zz,.08,1.2,.1)
            box('World',white,xx,.96,zz,.12,.15,.12)
    if ss<180:
        for side in [-1,1]:box('World',steel,side*10.5,.9,-ss-2.5,.10,.18,5)
    if ss%20==0:
        xx,zz=route(ss)
        # Flat arrow, all actual geometry on the road.
        tri('World',chevron,(xx-.48,.085,zz+.8),(xx+.48,.085,zz+.8),(xx,.085,zz-.9))
# Sculpted original pickup proxy. Negative Z is forward; dimensions are meters.
def loft(mat,sections):
    # Each section has z, bottom, top, lower half-width, upper half-width.
    rings=[[( -lo,b,z),(lo,b,z),(hi,t,z),(-hi,t,z)] for z,b,t,lo,hi in sections]
    quad('Truck',mat,*reversed(rings[0]));quad('Truck',mat,*rings[-1])
    for a,b in zip(rings,rings[1:]):
        for i in range(4):quad('Truck',mat,a[i],a[(i+1)%4],b[(i+1)%4],b[i])
def tube(mat,a,b,r,steps=10):
    axis=[b[i]-a[i] for i in range(3)];l=math.sqrt(sum(v*v for v in axis));axis=[v/l for v in axis]
    ref=(0,1,0) if abs(axis[1])<.9 else (1,0,0)
    u=[axis[1]*ref[2]-axis[2]*ref[1],axis[2]*ref[0]-axis[0]*ref[2],axis[0]*ref[1]-axis[1]*ref[0]]
    l=math.sqrt(sum(v*v for v in u));u=[v/l for v in u]
    v=[axis[1]*u[2]-axis[2]*u[1],axis[2]*u[0]-axis[0]*u[2],axis[0]*u[1]-axis[1]*u[0]]
    def point(p,t):return tuple(p[j]+r*(u[j]*math.cos(t)+v[j]*math.sin(t)) for j in range(3))
    for i in range(steps):
        t=i*2*math.pi/steps;tt=(i+1)*2*math.pi/steps
        quad('Truck',mat,point(a,t),point(a,tt),point(b,tt),point(b,t))
loft(black,[(-2.6,.5,.9,.93,1.0),(-2.35,.5,.95,1.04,1.08),(2.35,.5,.95,1.04,1.08),(2.55,.6,.88,.92,.98)])
loft(red,[(-2.5,.91,1.36,.99,.94),(-2.1,.83,1.55,1.08,1.02),(-1.4,.87,1.61,1.1,1.02),(.8,.87,1.6,1.1,1.02),(2.4,.95,1.54,1.06,1.01)])
# Sloped windshield and rear cab, red roof and pillars.
loft(glass,[(-1.38,1.58,1.66,1.0,.97),(-.94,1.58,2.38,1.0,.85),(.52,1.58,2.38,1.0,.85),(.84,1.58,1.73,1.0,.97)])
loft(red,[(-.98,2.36,2.4,.85,.82),(-.8,2.37,2.46,.87,.83),(.42,2.37,2.46,.87,.83),(.57,2.36,2.4,.85,.82)])
for side in [-1,1]:
    tube(red,(side*.99,1.58,-1.38),(side*.86,2.39,-.94),.055)
    tube(red,(side*.99,1.58,.84),(side*.86,2.39,.52),.065)
    tube(black,(side*.98,1.6,-.19),(side*.865,2.38,-.19),.05)
    box('Truck',black,side*1.14,1.79,-1.0,.27,.18,.28)
    for zz in [-.7,.38]:box('Truck',black,side*1.035,1.43,zz,.028,.045,.2)
    box('Truck',black,side*1.11,.77,-.1,.15,.09,2.4)
    # Raised bedside with a dark lined bed and longitudinal ridges.
    box('Truck',red,side*1.00,1.48,1.55,.15,.45,1.7)
    box('Truck',black,side*1.01,1.715,1.55,.17,.045,1.7)
    for zz in [-1.64,1.65]:
        # Wide wheel arches follow the top half of the tire.
        for i in range(12):
            a=i*math.pi/12;b=(i+1)*math.pi/12
            tube(black,(side*1.10,.67+.72*math.sin(a),zz+.72*math.cos(a)),(side*1.10,.67+.72*math.sin(b),zz+.72*math.cos(b)),.105,6)
    # Tubular black sports bar with sloped supports behind the cab.
    tube(black,(side*.9,1.7,1.38),(side*.8,2.48,.90),.07)
    tube(black,(side*.8,2.48,.90),(side*.72,2.57,.82),.07)
    tube(black,(side*.9,1.7,1.9),(side*.8,2.48,.90),.045)
tube(black,(-.72,2.57,.82),(.72,2.57,.82),.075)
box('Truck',black,0,2.65,.82,1.3,.09,.13)
for xx in [-.48,-.24,0,.24,.48]:box('Truck',headlamp,xx,2.65,.747,.14,.038,.018)
box('Truck',black,0,1.3,1.57,1.85,.075,1.58)
for xx in [-.7,-.35,0,.35,.7]:box('Truck',black,xx,1.35,1.57,.04,.025,1.58)
box('Truck',red,0,1.46,2.38,1.98,.48,.13)
box('Truck',black,0,1.6,2.455,.25,.045,.015)
box('Truck',black,0,.79,2.53,2.10,.20,.16)
box('Truck',steel,0,.65,2.54,1.16,.06,.12)
box('Truck',black,0,1.03,2.462,.38,.15,.01)
# Tailgate TRX mark built from original thin strokes, not a texture.
for a,b in [((-.38,1.4),(-.12,1.4)),((-.25,1.4),(-.25,1.23)),((-.05,1.23),(-.05,1.4)),((-.05,1.4),(.09,1.4)),((.09,1.4),(.09,1.32)),((.09,1.32),(-.05,1.32)),((0,1.32),(.13,1.23)),((.20,1.4),(.38,1.23)),((.20,1.23),(.38,1.4))]:tube(chrome,(a[0],a[1],2.455),(b[0],b[1],2.455),.012,6)
for side in [-1,1]:
    box('Truck',black,side*.89,1.44,2.458,.20,.40,.032)
    for xx in [side*.82,side*.95]:box('Truck',lamp,xx,1.44,2.481,.03,.34,.014)
    for yy in [1.28,1.60]:box('Truck',lamp,side*.885,yy,2.481,.15,.03,.014)
    tube(black,(side*.82,.56,2.42),(side*.82,.56,2.67),.095,16)
    box('Truck',headlamp,side*.80,1.37,-2.512,.33,.105,.016)
box('Truck',black,0,1.30,-2.512,1.18,.32,.018)
for xx in [-.48,-.24,0,.24,.48]:box('Truck',steel,xx,1.30,-2.526,.025,.27,.02)
loft(black,[(-2.0,1.548,1.555,.43,.43),(-1.55,1.61,1.7,.35,.3),(-1.35,1.62,1.67,.32,.3)])
for x in [-1.09,1.09]:
    for z in [-1.64,1.65]:
        r=.64;half=.25;steps=32
        for i in range(steps):
            a=2*math.pi*i/steps;b=2*math.pi*(i+1)/steps
            p=lambda xx,t,rr=r:(xx,.65+rr*math.cos(t),z+rr*math.sin(t))
            quad('Truck',black,p(x-half,a),p(x-half,b),p(x+half,b),p(x+half,a))
            tri('Truck',black,(x+half,.65,z),p(x+half,a),p(x+half,b))
            tri('Truck',black,(x-half,.65,z),p(x-half,b),p(x-half,a))
            if i%2==0:
                tube(black,p(x-half,a,r+.025),p(x+half,a+.03,r+.025),.035,6)
        outer=x+(.26 if x>0 else -.26)
        for i in range(8):
            a=i*math.pi/4
            tube(steel,(outer,.65,z),(outer,.65+.37*math.cos(a),z+.37*math.sin(a)),.045,6)
        tube(steel,(outer-.02,.65,z),(outer+.02,.65,z),.115,12)
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
