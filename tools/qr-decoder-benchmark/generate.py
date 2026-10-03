"""Deterministic synthetic camera corpus. Run after :app:compileDebugKotlin."""
from pathlib import Path
import os
import shutil
import subprocess
import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent.parent
OUT = ROOT / "out"
ASSETS = OUT / "assets"
JAVA = Path(r"C:\Program Files\Android\Android Studio1\jbr\bin\java.exe")
CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"
classes = REPO / "app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
stdlib = next((CACHE / "org.jetbrains.kotlin/kotlin-stdlib/2.3.20").rglob("kotlin-stdlib-2.3.20.jar"))
zxing = next((CACHE / "com.google.zxing/core/3.5.4").rglob("core-3.5.4.jar"))
cp = os.pathsep.join(map(str, [classes, stdlib, zxing]))
OUT.mkdir(exist_ok=True)
ASSETS.mkdir(exist_ok=True)
subprocess.run([str(JAVA.with_name("javac.exe")), "-encoding", "UTF-8", "-cp", cp,
                "-d", str(OUT), str(ROOT / "BenchmarkImages.java")], check=True)
subprocess.run([str(JAVA), "-cp", str(OUT)+os.pathsep+cp,
                "BenchmarkImages", str(OUT / "base")], check=True)
for name in ["qr/QrCameraDecoder.kt", "logic/QrShareCodec.kt"]:
    source = ROOT / "reference/QrCameraDecoder.kt" if name.startswith("qr/") else REPO / "app/src/main/java/jp/linkserver/nittcsc" / name
    target = OUT / "app-sources/jp/linkserver/nittcsc" / name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)

records = []
def warped(image, strength):
    n = image.width
    src = [(0, 0), (n, 0), (n, n), (0, n)]
    dst = [(n*strength/2, 0), (n*(1-strength/2), n*strength*.15),
           (n, n), (0, n*(1-strength*.15))]
    a, b = [], []
    for (x,y),(u,v) in zip(dst,src):
        a.extend([[x,y,1,0,0,0,-u*x,-u*y], [0,0,0,x,y,1,-v*x,-v*y]])
        b.extend([u,v])
    coefficients = np.linalg.solve(a,b)
    return image.transform(image.size, Image.Transform.PERSPECTIVE, coefficients,
                           Image.Resampling.BICUBIC, fillcolor=255)

def add(group, base, size=384, angle=0, perspective=0, blur=0,
        noise=0, contrast=1, gradient=0, inverted=False, offset=(0,0),
        canvas_size=(800,600), stream="", negative=False):
    # Integer module scaling first, matching QrImageEncoding's nearest-neighbour display.
    qr = Image.open(OUT / "base" / (base+".png")).convert("L")
    modules = qr.width
    scale = max(1, size // modules)
    qr = qr.resize((modules*scale, modules*scale), Image.Resampling.NEAREST)
    tile = Image.new("L", (size,size), 255)
    tile.paste(qr, ((size-qr.width)//2, (size-qr.height)//2))
    if perspective: tile = warped(tile, perspective)
    if angle: tile = tile.rotate(angle, Image.Resampling.BICUBIC, expand=True, fillcolor=255)
    image = Image.new("L",canvas_size,255)
    image.paste(tile, ((image.width-tile.width)//2+offset[0],
                      (image.height-tile.height)//2+offset[1]))
    if blur: image = image.filter(ImageFilter.GaussianBlur(blur))
    array = np.asarray(image).astype(float)
    array = 128 + (array-128)*contrast
    if gradient: array += np.linspace(-gradient,gradient,image.width)[None,:]
    if noise: array += np.random.default_rng(1000+len(records)).normal(0,noise,array.shape)
    if inverted: array = 255-array
    if negative: array[:] = 255
    image = Image.fromarray(np.clip(array,0,255).astype(np.uint8))
    ident = f"case{len(records):04}"
    image.save(ASSETS/(ident+".png"))
    expected = "" if negative else (OUT/"base"/(base+".txt")).read_text()
    (ASSETS/(ident+".txt")).write_text(expected)
    details=f"bytes={base},size={size},angle={angle},perspective={perspective},blur={blur},noise={noise},contrast={contrast},gradient={gradient},inverted={inverted},offset={offset}"
    records.append([ident,group,ident+".png",ident+".txt",details,stream])

for seed in range(2):
    for size in [180,256,384]:
        for angle in range(0,360,15): add("rotation_camera",f"b200s{seed}",size,angle)
    for density in [600,1600]:
        for angle in range(0,360,15): add("rotation_dense",f"b{density}s{seed}",512,angle,canvas_size=(960,960))
    for size in [180,256,384]:
        for angle in [0,30,45]:
            for strength in [.15,.3,.45,.6]: add("perspective",f"b200s{seed}",size,angle,perspective=strength)
            for sigma in [.5,1,1.5,2]: add("blur",f"b200s{seed}",size,angle,blur=sigma)
    for size in [100,128,160,200]:
        for angle in [0,30,45]: add("small",f"b200s{seed}",size,angle)
    for angle in [0,30,45]:
        for noise in [10,25,45]: add("noise",f"b200s{seed}",256,angle,noise=noise)
        for contrast in [.5,.25,.1]: add("contrast",f"b200s{seed}",256,angle,contrast=contrast)
        for gradient in [60,120]: add("lighting",f"b200s{seed}",256,angle,contrast=.5,gradient=gradient)
        add("inverted",f"b200s{seed}",256,angle,inverted=True)
        for offset in [(-235,-160),(235,160)]: add("off_center",f"b200s{seed}",256,angle,offset=offset)
        add("combined",f"b200s{seed}",256,angle,perspective=.3,blur=.7,noise=10,contrast=.5)
        add("hd_frame",f"b200s{seed}",384,angle,canvas_size=(1920,1080))
add("negative", "b200s0", negative=True)
# Sequential fragments exercise tracking and the production collector (two complete loops).
count=int((OUT/"base/stream-count.txt").read_text())
for scenario, size, angle, blur, movement in [("clean",256,0,0,False),("diagonal",256,30,.5,False),
                                             ("moving",256,45,.5,True),("small",180,30,.5,False)]:
    for loop in range(2):
        for i in range(count):
            offset=((-180 if i%2 else 180),0) if movement else (0,0)
            add("stream",f"stream{i}",size,angle,blur=blur,offset=offset,stream=scenario)
(ASSETS/"cases.tsv").write_text("\n".join("\t".join(r) for r in records))
shutil.copyfile(OUT/"base/stream-json.txt",ASSETS/"stream-json.txt")
print(f"Generated {len(records)} cases, {count} fragments per stream")
