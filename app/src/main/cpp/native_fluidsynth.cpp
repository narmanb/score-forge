#include <jni.h>
#include <fluidsynth.h>
#include <cstdint>
#include <string>
#include <vector>

namespace {

struct FluidEngine {
    fluid_settings_t* settings = nullptr;
    fluid_synth_t* synth = nullptr;
};

FluidEngine* fromHandle(jlong handle) {
    return reinterpret_cast<FluidEngine*>(static_cast<intptr_t>(handle));
}

jlong toHandle(FluidEngine* engine) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(engine));
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_scoreforge_app_audio_NativeFluidSynth_create(
    JNIEnv*,
    jobject,
    jint sampleRate
) {
    auto* engine = new FluidEngine();
    engine->settings = new_fluid_settings();
    if (engine->settings == nullptr) {
        delete engine;
        return 0;
    }

    fluid_settings_setnum(engine->settings, "synth.sample-rate", static_cast<double>(sampleRate));
    fluid_settings_setnum(engine->settings, "synth.gain", 0.7);

    engine->synth = new_fluid_synth(engine->settings);
    if (engine->synth == nullptr) {
        delete_fluid_settings(engine->settings);
        delete engine;
        return 0;
    }

    return toHandle(engine);
}

extern "C" JNIEXPORT void JNICALL
Java_com_scoreforge_app_audio_NativeFluidSynth_destroy(
    JNIEnv*,
    jobject,
    jlong handle
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr) return;

    if (engine->synth != nullptr) delete_fluid_synth(engine->synth);
    if (engine->settings != nullptr) delete_fluid_settings(engine->settings);
    delete engine;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_scoreforge_app_audio_NativeFluidSynth_loadSoundFont(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring path
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || engine->synth == nullptr || path == nullptr) return -1;

    const char* chars = env->GetStringUTFChars(path, nullptr);
    if (chars == nullptr) return -1;

    const int result = fluid_synth_sfload(engine->synth, chars, 1);
    env->ReleaseStringUTFChars(path, chars);
    return result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_scoreforge_app_audio_NativeFluidSynth_listPresets(
    JNIEnv* env,
    jobject,
    jlong handle,
    jint soundFontId
) {
    auto* engine = fromHandle(handle);
    jclass stringClass = env->FindClass("java/lang/String");
    if (engine == nullptr || engine->synth == nullptr || stringClass == nullptr) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }

    fluid_sfont_t* sfont = fluid_synth_get_sfont_by_id(engine->synth, soundFontId);
    if (sfont == nullptr) return env->NewObjectArray(0, stringClass, nullptr);

    std::vector<std::string> rows;
    fluid_sfont_iteration_start(sfont);
    while (fluid_preset_t* preset = fluid_sfont_iteration_next(sfont)) {
        const int bank = fluid_preset_get_banknum(preset);
        const int program = fluid_preset_get_num(preset);
        const char* name = fluid_preset_get_name(preset);
        rows.emplace_back(
            std::to_string(bank) + "\t" +
            std::to_string(program) + "\t" +
            (name != nullptr ? name : "Unnamed preset")
        );
    }

    jobjectArray output = env->NewObjectArray(
        static_cast<jsize>(rows.size()),
        stringClass,
        nullptr
    );
    if (output == nullptr) return nullptr;

    for (jsize i = 0; i < static_cast<jsize>(rows.size()); ++i) {
        jstring value = env->NewStringUTF(rows[static_cast<size_t>(i)].c_str());
        env->SetObjectArrayElement(output, i, value);
        env->DeleteLocalRef(value);
    }
    return output;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_scoreforge_app_audio_NativeFluidSynth_selectPreset(
    JNIEnv*,
    jobject,
    jlong handle,
    jint soundFontId,
    jint channel,
    jint bank,
    jint program
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || engine->synth == nullptr) return FLUID_FAILED;
    return fluid_synth_program_select(
        engine->synth,
        channel,
        soundFontId,
        bank,
        program
    );
}

extern "C" JNIEXPORT jint JNICALL
Java_com_scoreforge_app_audio_NativeFluidSynth_programChange(
    JNIEnv*,
    jobject,
    jlong handle,
    jint channel,
    jint program
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || engine->synth == nullptr) return FLUID_FAILED;
    return fluid_synth_program_change(engine->synth, channel, program);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_scoreforge_app_audio_NativeFluidSynth_noteOn(
    JNIEnv*,
    jobject,
    jlong handle,
    jint channel,
    jint key,
    jint velocity
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || engine->synth == nullptr) return FLUID_FAILED;
    return fluid_synth_noteon(engine->synth, channel, key, velocity);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_scoreforge_app_audio_NativeFluidSynth_noteOff(
    JNIEnv*,
    jobject,
    jlong handle,
    jint channel,
    jint key
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || engine->synth == nullptr) return FLUID_FAILED;
    return fluid_synth_noteoff(engine->synth, channel, key);
}

extern "C" JNIEXPORT void JNICALL
Java_com_scoreforge_app_audio_NativeFluidSynth_allNotesOff(
    JNIEnv*,
    jobject,
    jlong handle,
    jint channel
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || engine->synth == nullptr) return;
    fluid_synth_all_notes_off(engine->synth, channel);
}

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_scoreforge_app_audio_NativeFluidSynth_renderStereo(
    JNIEnv* env,
    jobject,
    jlong handle,
    jint frames
) {
    auto* engine = fromHandle(handle);
    if (engine == nullptr || engine->synth == nullptr || frames <= 0) {
        return env->NewShortArray(0);
    }

    std::vector<jshort> pcm(static_cast<size_t>(frames) * 2u);
    const int result = fluid_synth_write_s16(
        engine->synth,
        frames,
        pcm.data(),
        0,
        2,
        pcm.data(),
        1,
        2
    );

    if (result != FLUID_OK) return env->NewShortArray(0);

    jshortArray output = env->NewShortArray(static_cast<jsize>(pcm.size()));
    if (output == nullptr) return nullptr;
    env->SetShortArrayRegion(output, 0, static_cast<jsize>(pcm.size()), pcm.data());
    return output;
}
