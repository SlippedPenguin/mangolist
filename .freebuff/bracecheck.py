import re, sys

path = sys.argv[1]
content = open(path, encoding='utf-8').read()
c = re.sub(r'//.*', '', content)
c = re.sub(r'/\*.*?\*/', '', c, flags=re.S)
c = re.sub(r'""".*?"""', '', c, flags=re.S)
c = re.sub(r"'(?:[^'\\]|\\.)*'", '', c)
c = re.sub(r'"(?:[^"\\]|\\.)*"', '', c)
opens = c.count('{')
closes = c.count('}')
print(f'{path}: opens={opens} closes={closes} delta={opens - closes}')
