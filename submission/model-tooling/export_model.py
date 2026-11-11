"""Export authorized original Llama 3.2 1B Instruct weights with ExecuTorch v1.0.0.

Run using the exporter virtualenv on Linux/WSL. No model downloads or credentials.
The source release determines Torch/TorchAO compatibility. Save pip freeze alongside output.
"""
import argparse
import hashlib
import json
from pathlib import Path
import base64
import subprocess
import sys
import zipfile

def sha256(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()

def verify_tokenizer(path, expected_tokens=128000):
    """Validate the original Llama tiktoken merge-rank file; semantic pairing still needs native generation."""
    ranks=set(); pieces=set()
    with Path(path).open('rb') as stream:
        for line in stream:
            fields=line.split()
            if len(fields)!=2: raise ValueError('Invalid original tokenizer merge-rank line')
            try:
                token=base64.b64decode(fields[0],validate=True);rank=int(fields[1])
            except Exception as error: raise ValueError('Invalid tokenizer encoding/rank') from error
            if not token or rank<0 or rank in ranks or token in pieces:
                raise ValueError('Duplicate/invalid tokenizer token or rank')
            ranks.add(rank);pieces.add(token)
    if ranks!=set(range(expected_tokens)):
        raise ValueError('Unexpected tokenizer vocabulary; expected original Llama 3 merge ranks')

def package(pte, tokenizer, output, quantization, provenance):
    if quantization not in ['8da4w','int8']: raise ValueError('Unsupported quantization')
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    paths = {'model.pte': Path(pte), 'tokenizer.model': Path(tokenizer)}
    for path in paths.values():
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f'Missing/empty artifact: {path}')
    manifest = {
        'version': 1, 'model': 'Llama-3.2-1B-Instruct', 'runtime': '1.0.0',
        'backend': 'xnnpack', 'contextLength': 2048, 'quantization': quantization,
        'files': {name: {'sha256': sha256(path), 'bytes': path.stat().st_size} for name, path in paths.items()},
        'provenance': provenance,
    }
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    bundle = output / f'adapt-llama-{quantization}.zip'
    with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_STORED) as archive:
        archive.write(output / 'manifest.json', 'manifest.json')
        for name, path in paths.items():
            archive.write(path, name)
    return bundle

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--weights', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--quantization', choices=['8da4w', 'int8'], default='8da4w')
    args = parser.parse_args()
    source, weights, output = args.source.resolve(), args.weights.resolve(), args.output.resolve()
    tag = subprocess.check_output(['git', '-C', str(source), 'describe', '--tags', '--exact-match'], text=True).strip()
    if tag != 'v1.0.0':
        raise ValueError('Exporter checkout must be exactly v1.0.0')
    if subprocess.check_output(['git', '-C', str(source), 'status', '--porcelain', '--untracked-files=no'], text=True).strip():
        raise ValueError('Exporter has tracked source modifications')
    modules=subprocess.check_output(['git','-C',str(source),'submodule','status','--recursive'],text=True)
    if any(line and line[0]!=' ' for line in modules.splitlines()):
        raise ValueError('Exporter submodules must match pinned commits and be initialized')
    for name in ['consolidated.00.pth', 'params.json', 'tokenizer.model']:
        if not (weights / name).is_file():
            raise ValueError(f'Authorized original model file required: {name}')
    params = json.loads((weights / 'params.json').read_text())
    if params.get('dim') != 2048 or params.get('n_layers') != 16 or params.get('vocab_size') != 128256:
        raise ValueError('Expected original Llama 3.2 1B parameter dimensions')
    verify_tokenizer(weights/'tokenizer.model')
    output.mkdir(parents=True, exist_ok=True)
    config = {
        'base': {'model_class': 'llama3_2', 'checkpoint': str(weights/'consolidated.00.pth'),
                 'params': str(weights/'params.json'), 'tokenizer_path': str(weights/'tokenizer.model'),
                 'metadata': '{"get_bos_id":128000,"get_eos_ids":[128009,128001]}'},
        'model': {'use_kv_cache': True, 'use_sdpa_with_kv_cache': True, 'dtype_override': 'fp32'},
        'export': {'max_seq_length': 2048, 'max_context_length': 2048,
                   'output_dir': str(output), 'output_name': 'model.pte'},
        'quantization': {'qmode': args.quantization, 'embedding_quantize': '4,32' if args.quantization=='8da4w' else '8,0'},
        'backend': {'xnnpack': {'enabled': True, 'extended_ops': True}},
        'debug': {'verbose': True},
    }
    if args.quantization == '8da4w':
        config['quantization']['group_size'] = 32
    config_path = output/'export-config.yaml'
    config_path.write_text(json.dumps(config, indent=2), encoding='utf-8')
    # JSON is valid YAML; Hydra reads the .yaml extension.
    command = [sys.executable, '-m', 'extension.llm.export.export_llm', '--config', str(config_path)]
    with (output/'export.log').open('w', encoding='utf-8') as log:
        subprocess.run(command, cwd=source, stdout=log, stderr=subprocess.STDOUT, check=True)
    provenance = {
        'exporterCommit': subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip(),
        'checkpointSha256': sha256(weights/'consolidated.00.pth'),
        'paramsSha256': sha256(weights/'params.json'),
        'tokenizerFormat': 'Original Llama tiktoken; 128000 unique contiguous merge ranks validated',
        'configSha256': sha256(config_path),
        'verification': 'Export completed; Android generation and quality validation still required',
    }
    (output/'environment.txt').write_text(subprocess.check_output([sys.executable,'-m','pip','freeze'],text=True),encoding='utf-8')
    print(package(output/'model.pte',weights/'tokenizer.model',output,args.quantization,provenance))

if __name__ == '__main__':
    main()
