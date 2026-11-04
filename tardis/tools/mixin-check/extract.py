import re,os,sys
base=sys.argv[1]
out=[]
for root,_,fs in os.walk(base):
  for f in fs:
    if not f.endswith('.java'): continue
    src=re.sub(r'(?m)^\s*//.*$','',open(os.path.join(root,f)).read())
    imports={m.group(1).split('.')[-1]:m.group(1) for m in re.finditer(r'import ([\w.]+);',src)}
    m=re.search(r'@Mixin\(\s*(?:value\s*=\s*)?\{?([\w.,\s]+?)\.class',src)
    if not m: continue
    tgt=m.group(1).strip()
    tclass=imports.get(tgt.split('.')[0],tgt)
    if '.' in tgt: tclass=imports.get(tgt.split('.')[0])+'$'+tgt.split('.',1)[1]
    # each injector annotation block
    for am in re.finditer(r'@(Inject|Redirect|ModifyArgs?|ModifyVariable|WrapOperation|ModifyExpressionValue|ModifyConstant|ModifyReturnValue|WrapWithCondition)\s*\((.*?)\)\s*(?:private|public|protected)',src,re.S):
      kind,body=am.group(1),am.group(2)
      meths=re.search(r'method\s*=\s*(\{[^}]*\}|"[^"]*")',body).group(1)
      meths=re.findall(r'"([^"]*)"',meths)
      at=re.search(r'@At\((.*?)\)\s*(?:,|\)|$)',body,re.S)
      atv='';att='';ordn='-1'
      if at:
        a=at.group(1)
        v=re.search(r'value\s*=\s*"(\w+)"',a) or re.search(r'^\s*"(\w+)"',a)
        atv=v.group(1) if v else ''
        t=re.search(r'target\s*=\s*"([^"]*)"',a); att=t.group(1) if t else ''
        o=re.search(r'ordinal\s*=\s*(\d+)',a); ordn=o.group(1) if o else '-1'
      line=src[:am.start()].count('\n')+1
      for me in meths:
        out.append('\t'.join([f+':'+str(line),tclass,me,kind,atv,att,ordn]))
print('\n'.join(out))
