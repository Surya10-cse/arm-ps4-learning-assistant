package ai.adapt.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.adapt.inference.ModelStore
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Adversarial container fixtures only: these files are never passed to ExecuTorch or called model inference. */
@RunWith(AndroidJUnit4::class)
class ModelImportTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val store get()=ModelStore(context)
    private fun hash(value: ByteArray)=MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private fun bundle(corrupt: Boolean=false,coercedVersion: Boolean=false,pathTraversal: Boolean=false): File {
        val model="container-fixture-only".toByteArray();val tokenizer="tokenizer-fixture".toByteArray()
        val files=JSONObject().put("model.pte",JSONObject().put("bytes",model.size).put("sha256",if(corrupt) "0".repeat(64) else hash(model)))
            .put("tokenizer.model",JSONObject().put("bytes",tokenizer.size).put("sha256",hash(tokenizer)))
        val manifest=JSONObject().put("version",if(coercedVersion) "1" else 1).put("model","Llama-3.2-1B-Instruct").put("runtime","1.0.0").put("backend","xnnpack").put("contextLength",2048).put("quantization","8da4w").put("files",files).put("provenance",JSONObject().put("testFixture",true))
        return File.createTempFile("container-test",".zip",context.cacheDir).also { output ->
            ZipOutputStream(output.outputStream()).use { zip ->
                mapOf("manifest.json" to manifest.toString().toByteArray(),(if(pathTraversal) "../model.pte" else "model.pte") to model,"tokenizer.model" to tokenizer).forEach { (name,bytes) -> zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry() }
            }
        }
    }
    @Before fun requireIsolatedUnprovisionedPackage() {
        check(context.packageName.endsWith(".validation"))
        Assume.assumeTrue(store.installed()==null)
    }
    @Test fun checksumFailureCannotReachRuntimeOrActivate() {
        val file=bundle(corrupt=true);var invoked=false
        try { assertThrows(IllegalArgumentException::class.java) { store.importBundle(Uri.fromFile(file)) { invoked=true } };assertFalse(invoked);assertNull(store.installed()) }
        finally { file.delete() }
    }
    @Test fun traversalAndCoercedManifestVersionAreRejected() {
        listOf(bundle(pathTraversal=true),bundle(coercedVersion=true)).forEach { file ->
            try { assertThrows(IllegalArgumentException::class.java) { store.importBundle(Uri.fromFile(file)) { fail("Invalid container reached runtime") } } }
            finally { file.delete() }
        }
        assertNull(store.installed())
    }
    @Test fun runtimeRejectionRollsBackPreparedFiles() {
        val file=bundle()
        try { assertThrows(IllegalStateException::class.java) { store.importBundle(Uri.fromFile(file)) { throw IllegalStateException("Intentional runtime rejection for rollback test") } };assertNull(store.installed()) }
        finally { file.delete() }
        val directories=File(context.filesDir,"models").listFiles().orEmpty().filter { it.isDirectory }
        assertTrue(directories.isEmpty())
    }
}
