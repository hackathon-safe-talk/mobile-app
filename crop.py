import sys
import os
import subprocess

try:
    from PIL import Image
except ImportError:
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'pillow'])
    from PIL import Image

def crop_img(name):
    base_dir = r'd:\SafeTalk\app\src\main\res\drawable'
    in_path = os.path.join(base_dir, name + '.png')
    out_path = os.path.join(base_dir, name + '_cropped.png')
    
    if not os.path.exists(in_path):
        print('Missing:', in_path)
        return
        
    try:
        img = Image.open(in_path).convert("RGBA")
        bbox = img.getbbox()
        if bbox:
            cropped = img.crop(bbox)
            w, h = cropped.size
            pad = int(max(w, h) * 0.05)
            max_dim = max(w + pad*2, h + pad*2)
            
            new_img = Image.new("RGBA", (max_dim, max_dim), (0, 0, 0, 0))
            offset = ((max_dim - w) // 2, (max_dim - h) // 2)
            new_img.paste(cropped, offset)
            new_img.save(out_path)
            print('Saved', out_path)
        else:
            print('Empty bbox', in_path)
    except Exception as e:
        print('Error:', e)

for img_name in ['safetalk_shield', 'shield_safe', 'shield_warning', 'shield_danger']:
    crop_img(img_name)
