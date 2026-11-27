# O código do app é pequeno: mantém tudo para não ter surpresa com o encolhimento.
-keep class com.aintradutor.app.** { *; }

# ONNX Runtime é chamado por código nativo (JNI): não pode ser renomeado nem removido.
-keep class ai.onnxruntime.** { *; }
