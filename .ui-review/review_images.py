"""Choose the last fixture render and assemble a compact visual-review sheet."""
from pathlib import Path
import re
import shutil
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parent
source=root/'screenshots-final-raw'
destination=root/'screenshots-final'
destination.mkdir(exist_ok=True)
latest={}
for path in source.glob('*.png'):
    match=re.match(r'^(.*?)(?: \((\d+)\))?$',path.stem)
    name=match.group(1); version=int(match.group(2) or 0)
    if name not in latest or version>latest[name][0]: latest[name]=(version,path)
for name,(_,path) in latest.items(): shutil.copyfile(path,destination/(name+'.png'))
names=['welcome','create-restore','saved-wallets','dashboard','send','receive','history','inheritance','recovery','settings','restore']
sheet=Image.new('RGB',(1200,1100*((len(names)+2)//3)),'#101419')
draw=ImageDraw.Draw(sheet)
for index,name in enumerate(names):
    im=Image.open(destination/(name+'.png')).convert('RGB')
    im=im.crop((0,0,im.width,min(im.height,2850)))
    im=im.resize((400,round(im.height*400/im.width)))
    x=(index%3)*400; y=(index//3)*1100
    draw.text((x+12,y+8),name,fill='white'); sheet.paste(im,(x,y+30))
sheet.save(root/'contact-sheet-final.png')
for name in ['large-font-dashboard','large-font-inheritance']:
    im=Image.open(destination/(name+'.png')).convert('RGB')
    im.crop((0,0,im.width,min(im.height,2200))).save(root/(name+'-top.png'))
print('Final screenshots selected:',len(latest))
