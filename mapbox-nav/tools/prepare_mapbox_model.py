"""Package our existing truck with explicit triangle indices for Mapbox's glTF importer."""
import json, struct
from pathlib import Path
root = Path(__file__).resolve().parents[2]
source = root / 'prototype-nav/models/apex-truck-v02.glb'
raw = source.read_bytes()
json_len = struct.unpack_from('<I', raw, 12)[0]
doc = json.loads(raw[20:20 + json_len])
bin_header = 20 + json_len
bin_len = struct.unpack_from('<I', raw, bin_header)[0]
binary = bytearray(raw[bin_header + 8:bin_header + 8 + bin_len])
for primitive in doc['meshes'][0]['primitives']:
    count = doc['accessors'][primitive['attributes']['POSITION']]['count']
    assert count % 3 == 0 and count < 65536
    binary.extend(b'\0' * (-len(binary) % 4))
    offset = len(binary)
    binary.extend(struct.pack('<' + 'H' * count, *range(count)))
    view = len(doc['bufferViews'])
    doc['bufferViews'].append({'buffer': 0, 'byteOffset': offset, 'byteLength': count * 2, 'target': 34963})
    accessor = len(doc['accessors'])
    doc['accessors'].append({'bufferView': view, 'componentType': 5123, 'count': count, 'type': 'SCALAR', 'min': [0], 'max': [count - 1]})
    primitive['indices'] = accessor
    primitive['mode'] = 4
binary.extend(b'\0' * (-len(binary) % 4))
doc['scene'] = 0
doc['buffers'][0]['byteLength'] = len(binary)
payload = json.dumps(doc, separators=(',', ':')).encode()
payload += b' ' * (-len(payload) % 4)
output = struct.pack('<III', 0x46546c67, 2, 28 + len(payload) + len(binary)) + struct.pack('<II', len(payload), 0x4e4f534a) + payload + struct.pack('<II', len(binary), 0x004e4942) + binary
(root / 'mapbox-nav/app/src/main/assets/apex-truck.glb').write_bytes(output)
print('Indexed truck prepared:', len(output), 'bytes')
