package com.sahr.ontology;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.IOException;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class OnnxTextVectorizer implements TextVectorizer {
    private static final Logger logger = Logger.getLogger(OnnxTextVectorizer.class.getName());

    private final OrtEnvironment env;
    private final OrtSession session;
    private final WordPieceTokenizer tokenizer;
    private final int maxTokens;
    private final int dimensions;

    OnnxTextVectorizer(byte[] modelBytes, List<String> vocab, int maxTokens) {
        this.env = OrtEnvironment.getEnvironment();
        this.tokenizer = new WordPieceTokenizer(vocab);
        this.maxTokens = Math.max(8, maxTokens);
        try {
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            this.session = env.createSession(modelBytes, options);
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to load ONNX model", e);
        }
        this.dimensions = resolveDimensions();
        logger.info(() -> "OnnxTextVectorizer ready. dims=" + dimensions + " maxTokens=" + this.maxTokens);
    }

    public static OnnxTextVectorizer fromResources(String modelResource,
                                                   String vocabResource,
                                                   int maxTokens) throws IOException {
        byte[] modelBytes = readAllBytes(resourceStream(modelResource));
        List<String> vocab = java.nio.file.Files.readAllLines(java.nio.file.Path.of(vocabResource));
        return new OnnxTextVectorizer(modelBytes, vocab, maxTokens);
    }

    public static OnnxTextVectorizer fromStreams(InputStream modelStream,
                                                 InputStream vocabStream,
                                                 int maxTokens) throws IOException {
        byte[] modelBytes = readAllBytes(modelStream);
        List<String> vocab = new java.io.BufferedReader(new java.io.InputStreamReader(vocabStream))
                .lines()
                .toList();
        return new OnnxTextVectorizer(modelBytes, vocab, maxTokens);
    }

    @Override
    public float[] vectorize(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimensions];
        }
        List<Integer> tokenIds = tokenizer.encode(text, maxTokens);
        long[] inputIds = new long[maxTokens];
        long[] attention = new long[maxTokens];
        for (int i = 0; i < tokenIds.size() && i < maxTokens; i++) {
            inputIds[i] = tokenIds.get(i);
            attention[i] = 1;
        }
        try {
            Map<String, OnnxTensor> inputs = buildInputs(inputIds, attention);
            try (OrtSession.Result result = session.run(inputs)) {
                return extractEmbedding(result);
            } finally {
                for (OnnxTensor tensor : inputs.values()) {
                    tensor.close();
                }
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to run ONNX model", e);
        }
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private Map<String, OnnxTensor> buildInputs(long[] inputIds, long[] attention) throws OrtException {
        Map<String, OnnxTensor> inputs = new HashMap<>();
        Set<String> names = session.getInputNames();
        if (names.contains("input_ids")) {
            inputs.put("input_ids", tensorFromLongs(inputIds));
        }
        if (names.contains("attention_mask")) {
            inputs.put("attention_mask", tensorFromLongs(attention));
        }
        if (names.contains("token_type_ids")) {
            inputs.put("token_type_ids", tensorFromLongs(new long[maxTokens]));
        }
        if (inputs.isEmpty()) {
            throw new IllegalStateException("ONNX model inputs not recognized: " + names);
        }
        return inputs;
    }

    private OnnxTensor tensorFromLongs(long[] values) throws OrtException {
        long[] shape = new long[]{1, values.length};
        return OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape);
    }

    private float[] extractEmbedding(OrtSession.Result result) throws OrtException {
        if (result.size() == 0) {
            return new float[dimensions];
        }
        for (Map.Entry<String, OnnxValue> entry : result) {
            Object raw = entry.getValue().getValue();
            float[] pooled = extractPooled(raw);
            if (pooled != null) {
                return pooled;
            }
        }
        Object raw = result.get(0).getValue();
        float[] pooled = extractPooled(raw);
        return pooled == null ? new float[dimensions] : pooled;
    }

    private float[] extractPooled(Object raw) {
        if (raw instanceof float[][]) {
            float[][] matrix = (float[][]) raw;
            if (matrix.length > 0) {
                return matrix[0];
            }
        }
        if (raw instanceof float[][][]) {
            float[][][] cube = (float[][][]) raw;
            if (cube.length == 0) {
                return null;
            }
            float[][] tokens = cube[0];
            if (tokens.length == 0) {
                return null;
            }
            int dim = tokens[0].length;
            float[] mean = new float[dim];
            for (float[] token : tokens) {
                for (int i = 0; i < dim; i++) {
                    mean[i] += token[i];
                }
            }
            for (int i = 0; i < dim; i++) {
                mean[i] /= tokens.length;
            }
            return mean;
        }
        return null;
    }

    private int resolveDimensions() {
        try {
            OnnxTensor dummy = tensorFromLongs(new long[maxTokens]);
            try {
                Map<String, OnnxTensor> inputs = new HashMap<>();
                for (String name : session.getInputNames()) {
                    inputs.put(name, dummy);
                }
                try (OrtSession.Result result = session.run(inputs)) {
                    Object raw = result.get(0).getValue();
                    if (raw instanceof float[][]) {
                        return ((float[][]) raw)[0].length;
                    }
                    if (raw instanceof float[][][]) {
                        return ((float[][][]) raw)[0][0].length;
                    }
                } finally {
                    for (OnnxTensor tensor : inputs.values()) {
                        tensor.close();
                    }
                }
            } finally {
                dummy.close();
            }
        } catch (Exception e) {
            logger.warning("Unable to resolve embedding dimensions; defaulting to 256: " + e.getMessage());
        }
        return 256;
    }

    private static byte[] readAllBytes(InputStream stream) throws IOException {
        if (stream == null) {
            throw new IOException("Missing resource stream");
        }
        return stream.readAllBytes();
    }

    private static InputStream resourceStream(String resourcePath) {
        return OnnxTextVectorizer.class.getClassLoader().getResourceAsStream(resourcePath);
    }

    private static final class WordPieceTokenizer {
        private final Map<String, Integer> vocab;
        private final int unkId;
        private final int clsId;
        private final int sepId;

        private WordPieceTokenizer(List<String> tokens) {
            this.vocab = new HashMap<>();
            int idx = 0;
            for (String token : tokens) {
                vocab.put(token.trim(), idx++);
            }
            this.unkId = vocab.getOrDefault("[UNK]", 0);
            this.clsId = vocab.getOrDefault("[CLS]", 101);
            this.sepId = vocab.getOrDefault("[SEP]", 102);
        }

        List<Integer> encode(String text, int maxTokens) {
            java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
            ids.add(clsId);
            for (String token : basicTokenize(text)) {
                ids.addAll(wordPiece(token));
                if (ids.size() >= maxTokens - 1) {
                    break;
                }
            }
            ids.add(sepId);
            return ids;
        }

        private List<String> basicTokenize(String text) {
            String normalized = text.toLowerCase(Locale.ROOT);
            normalized = normalized.replaceAll("[^a-z0-9\\s]", " ");
            normalized = normalized.trim();
            if (normalized.isEmpty()) {
                return List.of();
            }
            return List.of(normalized.split("\\s+"));
        }

        private List<Integer> wordPiece(String token) {
            if (vocab.containsKey(token)) {
                return List.of(vocab.get(token));
            }
            java.util.ArrayList<Integer> result = new java.util.ArrayList<>();
            int start = 0;
            while (start < token.length()) {
                int end = token.length();
                Integer found = null;
                while (start < end) {
                    String sub = token.substring(start, end);
                    if (start > 0) {
                        sub = "##" + sub;
                    }
                    Integer id = vocab.get(sub);
                    if (id != null) {
                        found = id;
                        break;
                    }
                    end--;
                }
                if (found == null) {
                    return List.of(unkId);
                }
                result.add(found);
                start = end;
            }
            return result;
        }
    }
}
