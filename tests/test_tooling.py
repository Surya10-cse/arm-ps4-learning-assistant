import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('export_model',ROOT/'models/export_model.py')
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
summary_spec=importlib.util.spec_from_file_location('summarize',ROOT/'scripts/summarize_benchmarks.py')
summary_module=importlib.util.module_from_spec(summary_spec)
summary_spec.loader.exec_module(summary_module)
metrics_spec=importlib.util.spec_from_file_location('metrics',ROOT/'scripts/sample_device_metrics.py')
metrics_module=importlib.util.module_from_spec(metrics_spec)
metrics_spec.loader.exec_module(metrics_module)

class PackagingTests(unittest.TestCase):
    def test_bundle_hashes_match_artifacts_and_contains_only_expected_entries(self):
        # These are packaging fixtures, never used as an inference model.
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)
            (p/'fixture.pte').write_bytes(b'packaging-test-only')
            (p/'fixture.tokenizer').write_bytes(b'tokenizer-fixture')
            bundle=module.package(p/'fixture.pte',p/'fixture.tokenizer',p/'out','8da4w',{'testOnly':True})
            with zipfile.ZipFile(bundle) as z:
                self.assertEqual(set(z.namelist()),{'manifest.json','model.pte','tokenizer.model'})
                manifest=json.loads(z.read('manifest.json'))
                self.assertEqual(manifest['files']['model.pte']['sha256'],module.sha256(p/'fixture.pte'))
                self.assertEqual(manifest['files']['model.pte']['bytes'],len(z.read('model.pte')))
    def test_missing_artifact_cannot_be_packaged(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)
            with self.assertRaises(ValueError): module.package(p/'absent',p/'absent',p/'out','int8',{})
    def test_all_schemas_are_valid_json(self):
        for path in (ROOT/'schemas').glob('*.json'):
            doc=json.loads(path.read_text())
            self.assertEqual(doc['$schema'],'https://json-schema.org/draft/2020-12/schema')

class BenchmarkSummaryTests(unittest.TestCase):
    def row(self,purpose='measured',stats=None):
        return {'purpose':purpose,'device':'fixture-device','modelSha256':'a'*64,'promptSha256':'b'*64,'quantization':'8da4w','firstTextMs':100,'nativeStats':stats or {},'postGenerationPssKb':123}
    def test_empty_data_cannot_be_reported_as_a_benchmark(self):
        with self.assertRaises(ValueError): summary_module.summarize({'version':1,'measurements':[]})
    def test_missing_native_stats_do_not_become_estimated_tokens(self):
        result=summary_module.summarize({'version':1,'measurements':[self.row()]})
        self.assertIsNone(result['groups'][0]['medianNativeDecodeTokensPerSecond'])
        self.assertFalse(result['groups'][0]['peakMemoryMeasured'])
    def test_warmups_excluded_and_native_throughput_formula_used(self):
        stats={'inference_end_ms':2100,'prompt_eval_end_ms':100,'generated_tokens':10,'SCALING_FACTOR_UNITS_PER_SECOND':1000}
        result=summary_module.summarize({'version':1,'measurements':[self.row('warmup'),self.row(stats=stats)]})
        self.assertEqual(result['groups'][0]['runs'],1)
        self.assertEqual(result['groups'][0]['medianNativeDecodeTokensPerSecond'],5)
    def test_nonfinite_native_values_cannot_become_performance_results(self):
        row=self.row(stats={'inference_end_ms':float('inf'),'prompt_eval_end_ms':0,'generated_tokens':10,'SCALING_FACTOR_UNITS_PER_SECOND':1000})
        row['firstTextMs']=float('nan')
        result=summary_module.summarize({'version':1,'measurements':[row]})['groups'][0]
        self.assertIsNone(result['medianNativeDecodeTokensPerSecond']);self.assertIsNone(result['medianFirstTextMs'])

class TokenizerTests(unittest.TestCase):
    def test_contiguous_unique_merge_ranks_are_required(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'tokenizer.fixture'
            p.write_bytes(b'YQ== 0\nYg== 1\n')
            module.verify_tokenizer(p,expected_tokens=2)
            p.write_bytes(b'YQ== 0\nYg== 0\n')
            with self.assertRaises(ValueError):module.verify_tokenizer(p,expected_tokens=2)
    def test_wrong_tokenizer_format_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/'tokenizer.fixture';p.write_bytes(b'{"not":"a tokenizer.model"}')
            with self.assertRaises(ValueError):module.verify_tokenizer(p)

class DeviceMetricsTests(unittest.TestCase):
    def test_android_memory_formats_and_missing_samples(self):
        self.assertEqual(metrics_module.parse_pss('TOTAL PSS: 1234 TOTAL RSS: 4321'),1234)
        self.assertEqual(metrics_module.parse_pss('  TOTAL   456  123  0'),456)
        self.assertIsNone(metrics_module.parse_pss('No process found'))
    def test_proc_cpu_fields_with_spaces_in_process_name(self):
        fields=['S']+['0']*49
        fields[11]='120';fields[12]='30';fields[19]='10000'
        self.assertEqual(metrics_module.parse_cpu_ticks('123 (app name) '+' '.join(fields)),(150,10000))
        self.assertIsNone(metrics_module.parse_cpu_ticks('Permission denied'))

if __name__=='__main__': unittest.main()
