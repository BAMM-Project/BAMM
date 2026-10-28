"""Construction du MLP Keras et export TensorFlow Lite."""
from __future__ import annotations

import numpy as np


def build_mlp(input_dim: int, mean: np.ndarray, var: np.ndarray,
              units: tuple[int, int] = (32, 16), dropout: float = 0.2, lr: float = 1e-3):
    """Petit MLP : Normalization (StandardScaler intégré) → 2 denses + dropout → sigmoïde.

    La normalisation fait partie du graphe : le .tflite reçoit des valeurs brutes,
    l'app Android n'a donc pas à réimplémenter le scaler.
    """
    import tensorflow as tf
    from tensorflow import keras

    model = keras.Sequential([
        keras.Input(shape=(input_dim,), name="features"),
        keras.layers.Normalization(mean=mean, variance=var, name="scaler"),
        keras.layers.Dense(units[0], activation="relu"),
        keras.layers.Dropout(dropout),
        keras.layers.Dense(units[1], activation="relu"),
        keras.layers.Dropout(dropout),
        keras.layers.Dense(1, activation="sigmoid", name="risk"),
    ])
    model.compile(optimizer=keras.optimizers.Adam(lr), loss="binary_crossentropy",
                  metrics=[keras.metrics.Recall(name="recall"), keras.metrics.AUC(name="auc")])
    tf.random.set_seed(42)
    return model


def class_weights(y: np.ndarray) -> dict[int, float]:
    """Équivalent de class_weight='balanced' de scikit-learn."""
    n, pos = len(y), int(y.sum())
    return {0: n / (2 * (n - pos)), 1: n / (2 * pos)}


def fit_mlp(model, X, y, X_val=None, y_val=None, epochs: int = 120, verbose: int = 0):
    from tensorflow import keras

    es = keras.callbacks.EarlyStopping(monitor="val_loss", patience=12, restore_best_weights=True)
    val = (X_val, y_val) if X_val is not None else None
    return model.fit(X, y, validation_data=val, validation_split=0.0 if val else 0.15,
                     epochs=epochs, batch_size=64, class_weight=class_weights(y),
                     callbacks=[es], verbose=verbose)


def to_tflite(model) -> bytes:
    import tensorflow as tf

    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]  # quantification dynamique des poids
    return conv.convert()


def tflite_predict(tflite_bytes: bytes, X: np.ndarray) -> np.ndarray:
    """Inférence avec l'interpréteur TFLite — vérifie que l'export = le modèle évalué."""
    import tensorflow as tf

    it = tf.lite.Interpreter(model_content=tflite_bytes)
    inp, out = it.get_input_details()[0], it.get_output_details()[0]
    it.resize_tensor_input(inp["index"], [len(X), X.shape[1]])
    it.allocate_tensors()
    it.set_tensor(inp["index"], X.astype(np.float32))
    it.invoke()
    return it.get_tensor(out["index"]).ravel()
