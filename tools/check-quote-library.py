"""Offline verification of quote records against archived full source sections."""
import collections,hashlib,json,pathlib,re,sys
root=pathlib.Path(__file__).resolve().parents[1]
quote_file=root/'apps/particle-dream/src/main/res/raw/quotes.json'
q=json.loads(quote_file.read_text());old=json.loads((root/'docs/quote-evidence/previous-100.json').read_text());sections=json.loads((root/'docs/quote-evidence/sections.json').read_text())
def norm(s):return re.sub(r'\s+([,.;:!?])',r'\1',re.sub(r'\s+',' ',re.sub(r'\[\s*\d+\s*\]','',s.replace('\u200b',''))).strip())
def sig(s):return ' '.join(re.findall(r'\w+',s.casefold()))
assert len(q)==1000
assert all(x in q for x in old) and len(old)==100
assert collections.Counter(x['author'] for x in q)=={'Марк Аврелий':350,'Сенека':400,'Эпиктет':250}
fields={'text','author','work','source','translation','source_excerpt','verified'}
for i,x in enumerate(q):
 assert fields<=set(x) and (set(x)==fields or x in old),i
 assert x['verified']=='2026-09-06' and x['translation']=='own Russian translation',i
 assert x['source'].startswith('https://') and len(x['text'])<=280,i
 assert norm(x['source_excerpt']) in norm(sections[x['work']]),(i,x['work'])
for f in ['text','source_excerpt']:assert len(set(sig(x[f]) for x in q))==1000,f
for i,x in enumerate(q):
 a=sig(x['source_excerpt'])
 for y in q[:i]:
  if x['author']==y['author']:
   b=sig(y['source_excerpt']);assert a not in b and b not in a,(x['work'],y['work'])
print('PASS: 1000; original100 unchanged; 350/400/250; metadata; 1000 SAME-section excerpts; unique text/excerpts; no substring duplicates')
print('sha256='+hashlib.sha256(quote_file.read_bytes()).hexdigest())
