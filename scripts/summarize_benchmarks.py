"""Summarize actual Device > Export records; rejects absent measurements.

Decode throughput uses native generated tokens and native decode interval, never
callback counts or word counts. Post-generation PSS is not peak memory.
"""
import argparse, json, math, statistics
from pathlib import Path

def summarize(data):
    if data.get('version')!=1 or not data.get('measurements'):
        raise ValueError('No measured inference records; benchmark remains pending')
    groups={}
    for row in data['measurements']:
        if row.get('purpose')!='measured':
            continue
        key=(row['device'],row['modelSha256'],row['promptSha256'],row['quantization'])
        groups.setdefault(key,[]).append(row)
    if not groups:
        raise ValueError('No completed measured-suite runs; tutor/cold/warmup runs are excluded')
    result=[]
    for (device, model_hash, prompt_hash, quantization), rows in groups.items():
        rates=[]
        for row in rows:
            s=row['nativeStats']
            try:
                duration=s['inference_end_ms']-s['prompt_eval_end_ms']
                tokens=s['generated_tokens']
                scale=s['SCALING_FACTOR_UNITS_PER_SECOND']
                if all(isinstance(v,(int,float)) and not isinstance(v,bool) and math.isfinite(v) for v in (duration,tokens,scale)) and duration>0 and tokens>0 and scale==1000:
                    rates.append(tokens*scale/duration)
            except (KeyError,TypeError):
                pass
        ttft=sorted(r['firstTextMs'] for r in rows if isinstance(r.get('firstTextMs'),(float,int)) and not isinstance(r['firstTextMs'],bool) and math.isfinite(r['firstTextMs']) and r['firstTextMs']>=0)
        result.append({'device':device,'modelSha256':model_hash,'promptSha256':prompt_hash,'quantization':quantization,'runs':len(rows),
            'medianFirstTextMs':statistics.median(ttft) if ttft else None,
            'p95FirstTextMs':ttft[math.ceil(0.95*len(ttft))-1] if ttft else None,
            'medianNativeDecodeTokensPerSecond':statistics.median(rates) if rates else None,
            'runsWithNativeThroughput':len(rates),
            'maximumObservedPostGenerationPssKb':max(r['postGenerationPssKb'] for r in rows),
            'peakMemoryMeasured':False,
            'note':'Prompt lengths/temperature may vary. Use the fixed protocol before comparing quantization variants.'})
    return {'version':1,'groups':result}

def main():
    p=argparse.ArgumentParser()
    p.add_argument('input',type=Path);p.add_argument('--output',type=Path,required=True)
    args=p.parse_args()
    result=summarize(json.loads(args.input.read_text(encoding='utf-8')))
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(args.output)
if __name__=='__main__':main()
