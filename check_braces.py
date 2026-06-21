import sys
lines = open(sys.argv[1], 'r').readlines()
depth = 0
for i, line in enumerate(lines, 1):
    opens = line.count('{')
    closes = line.count('}')
    depth += opens - closes
    if depth < 0:
        print(f'Line {i}: NEGATIVE depth={depth}')
    if i >= 390 and depth <= 3:
        print(f'Line {i:3d}: depth={depth:2d}  {line.rstrip()[:70]}')
print(f'Final depth={depth}')
