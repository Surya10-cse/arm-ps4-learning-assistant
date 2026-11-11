package ai.adapt.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.adapt.core.*
import ai.adapt.inference.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalRuntimeGateTest {
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AdaptApplication
    @Test fun officialExecuTorchNativeLibraryInitializesOnArm64() {
        assertTrue("arm64-v8a" in android.os.Build.SUPPORTED_ABIS)
        // The official class initializer loads the real packaged JNI library. No model or synthetic inference is used.
        val type=Class.forName("org.pytorch.executorch.extension.llm.LlmModule",true,app.classLoader)
        assertTrue(java.lang.reflect.Modifier.isNative(type.getDeclaredMethod("load").modifiers))
    }
    @Test fun missingModelReportsActionableFailureWithoutGeneratingText() = runBlocking {
        Assume.assumeTrue(app.tutor.store.installed()==null)
        var emitted=false
        try { app.tutor.generate("No model provisioned").collect { if(it is TutorEvent.Token) emitted=true };fail("Missing model accepted") }
        catch(error: IllegalArgumentException) { assertTrue(error.message.orEmpty().contains("Import")) }
        assertFalse(emitted)
        app.tutor.unload()
    }
    @Test fun realProvisionedLlamaExecutesAllFiveModesWithoutChangingMastery() = runBlocking {
        // SKIP is a blocked runtime gate, never a passed inference result.
        Assume.assumeTrue("Authorized Llama bundle has not been provisioned",app.tutor.store.installed()!=null)
        val before=app.repository.dao.mastery()
        try {
            for(mode in TutorMode.entries) {
                val prompt=TutorPrompt.build(TutorRequest(mode,Goal.EDGE_AI,Curriculum.skills.first(),0.2,emptyList(),emptyList(),"Explain a boundary condition",assessed=false))
                var text="";var complete=false
                app.tutor.generate(prompt).collect { event -> when(event) { is TutorEvent.Token -> text+=event.text;is TutorEvent.Complete -> complete=true } }
                assertTrue("No native text for $mode",text.isNotBlank());assertTrue(complete)
            }
            assertEquals(before,app.repository.dao.mastery())
        } finally { app.tutor.unload() }
    }
}
