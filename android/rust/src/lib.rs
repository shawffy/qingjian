use jni::objects::JObject;
use jni::sys::{jboolean, jchar, jint, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use qingjian_core::{dictionary::Dictionary, Engine};
use std::cell::RefCell;

thread_local! {
    static ENGINE: RefCell<Option<Engine>> = RefCell::new(None);
}

fn java_string(env: &mut JNIEnv, value: String) -> jstring {
    env.new_string(value)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_qingjian_android_QingjianInputMethodService_nativeInit(
    mut env: JNIEnv,
    _this: JObject,
) -> jboolean {
    let source = include_str!("../../../assets/lexicon/dict.tsv");
    match Dictionary::parse(source) {
        Ok(dictionary) => {
            ENGINE.with(|cell| {
                *cell.borrow_mut() = Some(Engine::new(dictionary));
            });
            JNI_TRUE
        }
        Err(error) => {
            let _ = env.throw_new(
                "java/lang/IllegalStateException",
                format!("青简词库加载失败: {error}"),
            );
            JNI_FALSE
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_qingjian_android_QingjianInputMethodService_nativePushChar(
    _env: JNIEnv,
    _this: JObject,
    ch: jchar,
) {
    if let Some(ch) = char::from_u32(ch as u32) {
        ENGINE.with(|cell| {
            if let Some(engine) = cell.borrow_mut().as_mut() {
                engine.push(ch);
            }
        });
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_qingjian_android_QingjianInputMethodService_nativeBackspace(
    _env: JNIEnv,
    _this: JObject,
) {
    ENGINE.with(|cell| {
        if let Some(engine) = cell.borrow_mut().as_mut() {
            engine.backspace();
        }
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_qingjian_android_QingjianInputMethodService_nativeClear(
    _env: JNIEnv,
    _this: JObject,
) {
    ENGINE.with(|cell| {
        if let Some(engine) = cell.borrow_mut().as_mut() {
            engine.clear();
        }
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_qingjian_android_QingjianInputMethodService_nativeComposition(
    mut env: JNIEnv,
    _this: JObject,
) -> jstring {
    let text = ENGINE.with(|cell| {
        cell.borrow()
            .as_ref()
            .map(|engine| engine.composition().text().to_owned())
            .unwrap_or_default()
    });
    java_string(&mut env, text)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_qingjian_android_QingjianInputMethodService_nativeCandidates(
    mut env: JNIEnv,
    _this: JObject,
) -> jstring {
    let candidates = ENGINE.with(|cell| {
        cell.borrow()
            .as_ref()
            .and_then(|engine| engine.query().ok())
            .map(|query| {
                query.candidates.items.iter()
                    .take(8)
                    .map(|candidate| candidate.text.as_str())
                    .collect::<Vec<_>>()
                    .join("\t")
            })
            .unwrap_or_default()
    });
    java_string(&mut env, candidates)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_qingjian_android_QingjianInputMethodService_nativeCommit(
    mut env: JNIEnv,
    _this: JObject,
    index: jint,
) -> jstring {
    if index < 0 {
        return java_string(&mut env, String::new());
    }

    let committed = ENGINE.with(|cell| {
        let mut slot = cell.borrow_mut();
        let Some(engine) = slot.as_mut() else {
            return String::new();
        };
        let candidate = match engine.query() {
            Ok(query) => query.candidates.items.get(index as usize).cloned(),
            Err(_) => None,
        };
        match candidate {
            Some(candidate) => engine.commit(&candidate),
            None => String::new(),
        }
    });
    java_string(&mut env, committed)
}
