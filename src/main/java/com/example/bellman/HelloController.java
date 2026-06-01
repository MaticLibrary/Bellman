package com.example.bellman;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.net.URL;
import java.util.*;

/**
 * Kontroler dla wizualizacji algorytmu Bellmana-Forda.
 * Umożliwia tworzenie grafu, edycję wag, uruchamianie algorytmu i krokowe animacje.
 */
public class HelloController implements Initializable {

    // ---------------------------- FXML INIECTIONS ----------------------------
    @FXML private Pane graphPane;
    @FXML private ComboBox<String> sourceCombo;
    @FXML private ComboBox<String> targetCombo;
    @FXML private TextField sourceField;
    @FXML private TextField targetField;
    @FXML private TextField weightField;
    @FXML private Button addNodeButton;
    @FXML private Button addEdgeModeButton;
    @FXML private Button deleteButton;
    @FXML private Button createEdgeButton;
    @FXML private Button runButton;
    @FXML private Button resetButton;
    @FXML private Button clearButton;
    @FXML private TextArea logArea;
    @FXML private Label statusLabel;
    @FXML private ComboBox<String> pathTypeCombo;
    @FXML private CheckBox directedGraphCheckbox;

    // ---------------------------- TRYBY I WARSTWY ----------------------------
    private enum Mode { ADD_NODE, ADD_EDGE, DELETE, NONE }
    private Mode currentMode = Mode.ADD_NODE;
    private Group edgeLayer;
    private Group nodeLayer;

    // ---------------------------- DANE GRAFU ----------------------------
    private int nextNodeId = 1;
    private final Map<Integer, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private GraphNode edgeSourceNode;          // dla trybu dodawania krawędzi
    private Timeline algorithmTimeline;
    private GraphEdge activeEdgeHighlight;     // aktualnie podświetlona krawędź podczas animacji

    // ---------------------------- INICJALIZACJA ----------------------------
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        edgeLayer = new Group();
        nodeLayer = new Group();
        graphPane.getChildren().addAll(edgeLayer, nodeLayer);

        // Kliknięcie w tło – dodanie węzła (tryb ADD_NODE)
        graphPane.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (currentMode == Mode.ADD_NODE) {
                Point2D point = graphPane.sceneToLocal(e.getSceneX(), e.getSceneY());
                if (!isPointOverNode(point)) {
                    createNode(point.getX(), point.getY());
                }
            }
        });

        weightField.setText("1");
        switchMode(Mode.ADD_NODE);
        updateSourceCombo();
        // populate target combo and path type options
        if (pathTypeCombo != null) {
            pathTypeCombo.getItems().setAll("Najtańsza", "Najdroższa");
            pathTypeCombo.setValue("Najtańsza");
        }
        if (targetCombo != null) updateTargetCombo();
        
        // Setup directed graph checkbox listener
        if (directedGraphCheckbox != null) {
            directedGraphCheckbox.setSelected(true); // default: directed
            directedGraphCheckbox.selectedProperty().addListener((obs, old, isDirected) -> {
                if (!edges.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Uwaga");
                    alert.setHeaderText("Graf zawiera krawędzie");
                    alert.setContentText("Zmiana trybu kierunkowości nie zmieni istniejących krawędzi. \nTryb dotyczy tylko nowych krawędzi.");
                    alert.showAndWait();
                }
                updateDirectivityStatus();
            });
            updateDirectivityStatus();
        }
        updateStatus("Tryb dodawania węzłów: kliknij w puste miejsce, aby dodać nowy węzeł.");
    }
    
    private void updateDirectivityStatus() {
        if (directedGraphCheckbox != null) {
            boolean isDirected = directedGraphCheckbox.isSelected();
            String mode = isDirected ? "SKIEROWANY (→)" : "DWUKIERUNKOWY (↔)";
            updateStatus("Tryb: " + mode);
        }
    }
    }

    // ---------------------------- OBSŁUGA TRYBÓW (PRZYCISKI) ----------------------------
    @FXML private void onAddNodeMode()   { switchMode(Mode.ADD_NODE); }
    @FXML private void onAddEdgeMode()   { switchMode(Mode.ADD_EDGE); }
    @FXML private void onDeleteMode()    { switchMode(Mode.DELETE); }
    @FXML private void onCreateEdgeByIds() {
        try {
            int srcId = Integer.parseInt(sourceField.getText().trim());
            int tgtId = Integer.parseInt(targetField.getText().trim());
            int weight = Integer.parseInt(weightField.getText().trim());
            GraphNode src = nodes.get(srcId);
            GraphNode tgt = nodes.get(tgtId);
            if (src == null || tgt == null) {
                updateStatus("Błąd: podaj istniejące identyfikatory węzłów.");
                return;
            }
            if (src == tgt) {
                updateStatus("Błąd: krawędź musi łączyć różne węzły.");
                return;
            }
            addEdge(src, tgt, weight);
        } catch (NumberFormatException e) {
            updateStatus("Błąd: ID i waga muszą być liczbami całkowitymi.");
        }
    }
    @FXML private void onRunAlgorithm()   { runPathSearch(); }
    @FXML private void onResetGraph()     { resetVisualization(); }
    @FXML private void onClearGraph()     { clearGraph(); }

    private void switchMode(Mode mode) {
        currentMode = mode;
        edgeSourceNode = null;
        highlightSelectedSource(null);
        String defaultStyle = "-fx-background-color: #dfe6f3; -fx-text-fill: black;";
        String activeStyle  = "-fx-background-color: #3f51b5; -fx-text-fill: white;";
        addNodeButton.setStyle(defaultStyle);
        addEdgeModeButton.setStyle(defaultStyle);
        deleteButton.setStyle(defaultStyle);

        switch (mode) {
            case ADD_NODE -> {
                addNodeButton.setStyle(activeStyle);
                updateStatus("Kliknij w puste miejsce, aby dodać nowy węzeł.");
            }
            case ADD_EDGE -> {
                addEdgeModeButton.setStyle(activeStyle);
                updateStatus("Kliknij węzeł źródłowy, a potem docelowy, aby dodać krawędź.");
            }
            case DELETE -> {
                deleteButton.setStyle(activeStyle);
                updateStatus("Kliknij węzeł, aby go usunąć.");
            }
            default -> updateStatus("Wybierz tryb działania.");
        }
    }

    // ---------------------------- OPERACJE NA WĘZŁACH I KRAWĘDZIACH ----------------------------
    private void createNode(double x, double y) {
        int id = nextNodeId++;
        GraphNode node = new GraphNode(id, x, y);
        nodes.put(id, node);
        nodeLayer.getChildren().add(node.view);
        updateSourceCombo();
        updateStatus("Dodano węzeł " + id + ". Możesz go przeciągnąć lub dodać krawędź.");
    }

    private void addEdge(GraphNode source, GraphNode target, int weight) {
        if (edges.stream().anyMatch(e -> e.source == source && e.target == target)) {
            updateStatus("Krawędź już istnieje: " + source.id + " -> " + target.id);
            return;
        }
        GraphEdge edge = new GraphEdge(source, target, weight);
        edges.add(edge);
        edgeLayer.getChildren().add(edge.view);
        edge.updatePosition();
        updateStatus("Dodano krawędź " + source.id + " -> " + target.id + ", waga = " + weight);
        if (currentMode == Mode.ADD_EDGE) {
            edgeSourceNode = null;
            highlightSelectedSource(null);
        }
        // if graph is undirected, add reverse edge as separate GraphEdge
        boolean directed = true;
        if (directedGraphCheckbox != null) directed = directedGraphCheckbox.isSelected();
        if (!directed) {
            boolean reverseExists = edges.stream().anyMatch(e -> e.source == target && e.target == source);
            if (!reverseExists) {
                GraphEdge rev = new GraphEdge(target, source, weight);
                edges.add(rev);
                edgeLayer.getChildren().add(rev.view);
                rev.updatePosition();
                updateStatus("Dodano krawędź dwukierunkową: " + source.id + " ↔ " + target.id + " (waga: " + weight + ")");
            }
        } else {
            updateStatus("Dodano krawędź skierowaną: " + source.id + " → " + target.id + " (waga: " + weight + ")");
        }
    }

    /** Obsługa kliknięcia prawym przyciskiem na krawędź – zmiana wagi */
    private void handleEdgeRightClick(GraphEdge edge) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(edge.weight));
        dialog.setTitle("Edycja wagi");
        dialog.setHeaderText("Zmiana wagi krawędzi " + edge.source.id + " → " + edge.target.id);
        dialog.setContentText("Nowa waga (liczba całkowita):");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(value -> {
            try {
                int newWeight = Integer.parseInt(value);
                edge.weight = newWeight;
                edge.text.setText(String.valueOf(newWeight));
                updateStatus("Zmieniono wagę krawędzi " + edge.source.id + "→" + edge.target.id + " na " + newWeight);
                // Odśwież wygląd (reset stylu)
                edge.setDefaultStyle();
            } catch (NumberFormatException e) {
                updateStatus("Błędna waga – musi być liczbą całkowitą.");
            }
        });
    }

    private void removeNode(GraphNode node) {
        // Usuń wszystkie krawędzie związane z węzłem
        edges.removeIf(edge -> {
            if (edge.source == node || edge.target == node) {
                edgeLayer.getChildren().remove(edge.view);
                return true;
            }
            return false;
        });
        nodeLayer.getChildren().remove(node.view);
        nodes.remove(node.id);
        updateSourceCombo();
        updateStatus("Usunięto węzeł " + node.id + " wraz z powiązanymi krawędziami.");
    }

    private void updateSourceCombo() {
        List<String> values = new ArrayList<>();
        for (Integer id : nodes.keySet()) values.add(String.valueOf(id));
        sourceCombo.getItems().setAll(values);
        if (!values.isEmpty() && !values.contains(sourceCombo.getValue())) {
            sourceCombo.setValue(values.get(0));
        }
        if (targetCombo != null) {
            targetCombo.getItems().setAll(values);
            if (!values.isEmpty() && !values.contains(targetCombo.getValue())) {
                // prefer a different default if possible
                if (values.size() > 1) targetCombo.setValue(values.get(1)); else targetCombo.setValue(values.get(0));
            }
        }
    }

    private void updateTargetCombo() {
        List<String> values = new ArrayList<>();
        for (Integer id : nodes.keySet()) values.add(String.valueOf(id));
        if (targetCombo != null) {
            targetCombo.getItems().setAll(values);
            if (!values.isEmpty() && !values.contains(targetCombo.getValue())) {
                if (values.size() > 1) targetCombo.setValue(values.get(1)); else targetCombo.setValue(values.get(0));
            }
        }
    }
    private boolean isPointOverNode(Point2D point) {
        return nodes.values().stream().anyMatch(n -> n.contains(point));
    }

    private void highlightSelectedSource(GraphNode node) {
        if (edgeSourceNode != null) edgeSourceNode.setSelected(false);
        edgeSourceNode = node;
        if (node != null) node.setSelected(true);
    }

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    // ---------------------------- ALGORYTM BELLMANA-FORDA ----------------------------
    private void startBellmanFord() {
        if (algorithmTimeline != null) algorithmTimeline.stop();
        if (nodes.isEmpty()) {
            updateStatus("Brak węzłów do analizy.");
            return;
        }
        String sourceValue = sourceCombo.getValue();
        if (sourceValue == null || sourceValue.isBlank()) {
            updateStatus("Wybierz źródło z listy.");
            return;
        }
        int sourceId = Integer.parseInt(sourceValue);
        if (!nodes.containsKey(sourceId)) {
            updateStatus("Wybrane źródło nie istnieje.");
            return;
        }

        resetVisualization();
        BellmanFordResult result = computeBellmanFord(sourceId);
        if (result.steps.isEmpty()) {
            updateStatus("Brak krawędzi do przetworzenia.");
            return;
        }
        if (result.negativeCycle) {
            logArea.appendText("UWAGA: wykryto ujemny cykl! Wyniki mogą być niepoprawne.\n");
        }
        runVisualization(result);
    }

    private BellmanFordResult computeBellmanFord(int sourceId) {
        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> pred = new HashMap<>();
        for (int id : nodes.keySet()) dist.put(id, Double.POSITIVE_INFINITY);
        dist.put(sourceId, 0.0);

        List<BFVisualStep> steps = new ArrayList<>();
        int n = nodes.size();

        // Relaksacje
        for (int i = 1; i < n; i++) {
            boolean changed = false;
            for (GraphEdge edge : edges) {
                double uDist = dist.get(edge.source.id);
                double vDist = dist.get(edge.target.id);
                boolean relax = (uDist != Double.POSITIVE_INFINITY && uDist + edge.weight < vDist);
                if (relax) {
                    dist.put(edge.target.id, uDist + edge.weight);
                    pred.put(edge.target.id, edge.source.id);
                    changed = true;
                }
                steps.add(new BFVisualStep(edge, relax, i));
            }
            if (!changed) break;
        }

        // Wykrywanie ujemnego cyklu
        boolean negativeCycle = false;
        Set<GraphEdge> cycleEdges = new HashSet<>();
        for (GraphEdge edge : edges) {
            double uDist = dist.get(edge.source.id);
            double vDist = dist.get(edge.target.id);
            if (uDist != Double.POSITIVE_INFINITY && uDist + edge.weight < vDist) {
                negativeCycle = true;
                cycleEdges.add(edge);
            }
        }

        return new BellmanFordResult(sourceId, dist, pred, steps, negativeCycle, cycleEdges);
    }

    // ---------------------------- EXHAUSTIVE PATH SEARCH (ALL SIMPLE PATHS) ----------------------------
    private static class PathSearchResult {
        List<GraphEdge> pathEdges;
        double weight;
        int pathsChecked;
        PathSearchResult(List<GraphEdge> pathEdges, double weight, int pathsChecked) {
            this.pathEdges = pathEdges;
            this.weight = weight;
            this.pathsChecked = pathsChecked;
        }
    }

    private void runPathSearch() {
        resetVisualization();
        if (nodes.isEmpty()) {
            updateStatus("Brak węzłów do analizy.");
            return;
        }
        String sourceValue = sourceCombo.getValue();
        String targetValue = (targetCombo != null ? targetCombo.getValue() : null);
        if (sourceValue == null || sourceValue.isBlank() || targetValue == null || targetValue.isBlank()) {
            updateStatus("Wybierz źródło i cel z listy.");
            return;
        }
        int sourceId = Integer.parseInt(sourceValue);
        int targetId = Integer.parseInt(targetValue);
        if (!nodes.containsKey(sourceId) || !nodes.containsKey(targetId)) {
            updateStatus("Wybrane węzły nie istnieją.");
            return;
        }

        boolean findMin = true;
        if (pathTypeCombo != null && "Najdroższa".equals(pathTypeCombo.getValue())) findMin = false;

        // detect cycles on any path between source and target
        boolean hasCycleOnPath = detectCycleOnPath(sourceId, targetId);
        if (hasCycleOnPath) {
            logArea.appendText("UWAGA: wykryto cykl możliwy do odwiedzenia na ścieżce między źródłem a celem.\n");
        }

        PathSearchResult result = computeAllSimplePaths(sourceId, targetId, findMin);
        if (!findMin && hasPositiveCycleOnPath(sourceId, targetId)) {
            logArea.appendText("UWAGA: najdroższa ścieżka może być nieograniczona z powodu dodatniego cyklu.\n");
        }
        if (result.pathEdges == null || result.pathEdges.isEmpty()) {
            logArea.appendText("Brak ścieżki między wybranymi węzłami.\n");
            updateStatus("Brak ścieżki między wybranymi węzłami.");
            return;
        }

        // Highlight resulting path
        edges.forEach(GraphEdge::setDefaultStyle);
        for (GraphEdge e : result.pathEdges) e.setSuccessStyle();

        // Build node sequence string
        StringBuilder sb = new StringBuilder();
        sb.append("Znaleziona ścieżka: ");
        sb.append(result.pathEdges.get(0).source.id);
        for (GraphEdge e : result.pathEdges) {
            sb.append(" -> ").append(e.target.id);
        }
        sb.append(String.format("  (waga: %.0f)\n", result.weight));
        sb.append(String.format("Sprawdzono %d ścieżek.\n", result.pathsChecked));
        logArea.appendText(sb.toString());
        updateStatus("Znaleziono ścieżkę.");
    }

    private PathSearchResult computeAllSimplePaths(int sourceId, int targetId, boolean findMin) {
        GraphNode source = nodes.get(sourceId);
        GraphNode target = nodes.get(targetId);
        List<GraphEdge>[] bestPathRef = new List[1];
        double[] bestWeightRef = new double[]{findMin ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY};
        int[] pathsChecked = {0};

        Set<Integer> visited = new HashSet<>();
        visited.add(sourceId);

        List<GraphEdge> current = new ArrayList<>();

        // DFS
        class Helper {
            void dfs(GraphNode node, double acc) {
                if (node == target) {
                    pathsChecked[0]++;
                    if (findMin) {
                        if (acc < bestWeightRef[0]) {
                            bestWeightRef[0] = acc;
                            bestPathRef[0] = new ArrayList<>(current);
                        }
                    } else {
                        if (acc > bestWeightRef[0]) {
                            bestWeightRef[0] = acc;
                            bestPathRef[0] = new ArrayList<>(current);
                        }
                    }
                    return;
                }
                for (GraphEdge e : edges) {
                    if (e.source != node) continue;
                    int nid = e.target.id;
                    if (visited.contains(nid)) continue; // only simple paths
                    visited.add(nid);
                    current.add(e);
                    dfs(e.target, acc + e.weight);
                    current.remove(current.size() - 1);
                    visited.remove(nid);
                }
            }
        }
        new Helper().dfs(source, 0.0);

        return new PathSearchResult(bestPathRef[0] == null ? Collections.emptyList() : bestPathRef[0], bestWeightRef[0], pathsChecked[0]);
    }

    private boolean detectCycleOnPath(int sourceId, int targetId) {
        // nodes reachable from source
        Set<Integer> fromSource = bfsForward(sourceId);
        // nodes that can reach target (reverse BFS)
        Set<Integer> toTarget = bfsReverse(targetId);
        fromSource.retainAll(toTarget);
        if (fromSource.isEmpty()) return false;
        return hasCycleInSubgraph(fromSource);
    }

    private boolean hasPositiveCycleOnPath(int sourceId, int targetId) {
        Set<Integer> fromSource = bfsForward(sourceId);
        Set<Integer> toTarget = bfsReverse(targetId);
        fromSource.retainAll(toTarget);
        if (fromSource.isEmpty()) return false;
        return hasPositiveCycleInSubgraph(fromSource);
    }

    private boolean hasPositiveCycleInSubgraph(Set<Integer> subset) {
        Set<Integer> stack = new HashSet<>();
        Deque<GraphEdge> path = new ArrayDeque<>();
        for (int start : subset) {
            if (dfsPositiveCycle(start, start, stack, path, subset)) return true;
        }
        return false;
    }

    private boolean dfsPositiveCycle(int start, int current, Set<Integer> stack, Deque<GraphEdge> path, Set<Integer> subset) {
        if (stack.contains(current)) {
            return false;
        }
        stack.add(current);
        for (GraphEdge edge : edges) {
            if (edge.source.id != current) continue;
            int next = edge.target.id;
            if (!subset.contains(next)) continue;
            path.addLast(edge);
            if (next == start) {
                double cycleWeight = 0;
                for (GraphEdge e : path) cycleWeight += e.weight;
                if (cycleWeight > 0) {
                    path.removeLast();
                    stack.remove(current);
                    return true;
                }
            } else if (!stack.contains(next)) {
                if (dfsPositiveCycle(start, next, stack, path, subset)) {
                    path.removeLast();
                    stack.remove(current);
                    return true;
                }
            }
            path.removeLast();
        }
        stack.remove(current);
        return false;
    }

    private Set<Integer> bfsForward(int startId) {
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> q = new ArrayDeque<>();
        seen.add(startId); q.add(startId);
        while (!q.isEmpty()) {
            int cur = q.poll();
            for (GraphEdge e : edges) if (e.source.id == cur) {
                int nid = e.target.id;
                if (!seen.contains(nid)) { seen.add(nid); q.add(nid); }
            }
        }
        return seen;
    }

    private Set<Integer> bfsReverse(int targetId) {
        // build reverse adjacency via edges
        Map<Integer, List<Integer>> rev = new HashMap<>();
        for (GraphEdge e : edges) rev.computeIfAbsent(e.target.id, k -> new ArrayList<>()).add(e.source.id);
        Set<Integer> seen = new HashSet<>();
        Deque<Integer> q = new ArrayDeque<>();
        seen.add(targetId); q.add(targetId);
        while (!q.isEmpty()) {
            int cur = q.poll();
            List<Integer> preds = rev.getOrDefault(cur, Collections.emptyList());
            for (int p : preds) if (!seen.contains(p)) { seen.add(p); q.add(p); }
        }
        return seen;
    }

    private boolean hasCycleInSubgraph(Set<Integer> subset) {
        Map<Integer, Integer> color = new HashMap<>(); // 0=white,1=gray,2=black
        for (int v : subset) color.put(v, 0);
        for (int v : subset) {
            if (color.get(v) == 0) {
                if (dfsCycle(v, color, subset)) return true;
            }
        }
        return false;
    }

    private boolean dfsCycle(int v, Map<Integer, Integer> color, Set<Integer> subset) {
        color.put(v, 1);
        for (GraphEdge e : edges) {
            if (e.source.id != v) continue;
            int to = e.target.id;
            if (!subset.contains(to)) continue;
            if (color.getOrDefault(to,0) == 1) return true;
            if (color.getOrDefault(to,0) == 0) {
                if (dfsCycle(to, color, subset)) return true;
            }
        }
        color.put(v, 2);
        return false;
    }

    private void runVisualization(BellmanFordResult result) {
        logArea.clear();
        // Reset odległości w węzłach
        nodes.values().forEach(n -> n.updateDistance(Double.POSITIVE_INFINITY));
        nodes.get(result.sourceId).updateDistance(0.0);
        edges.forEach(GraphEdge::setDefaultStyle);
        activeEdgeHighlight = null;

        algorithmTimeline = new Timeline();
        graphPane.setDisable(true);  // blokada interakcji podczas animacji

        int stepIndex = 0;
        for (BFVisualStep step : result.steps) {
            double time = stepIndex * 320.0;
            algorithmTimeline.getKeyFrames().add(new KeyFrame(Duration.millis(time), e -> applyStep(step)));
            stepIndex++;
        }
        algorithmTimeline.getKeyFrames().add(new KeyFrame(Duration.millis(stepIndex * 320.0 + 300), e -> finishVisualization(result)));
        algorithmTimeline.play();
    }

    private void applyStep(BFVisualStep step) {
        if (activeEdgeHighlight != null) activeEdgeHighlight.restoreAfterHighlight();
        activeEdgeHighlight = step.edge;
        activeEdgeHighlight.highlightActive();

        if (step.relax) {
            double newDist = activeEdgeHighlight.source.distance + activeEdgeHighlight.weight;
            activeEdgeHighlight.target.updateDistance(newDist);
            activeEdgeHighlight.markRelaxed();
            logArea.appendText(String.format("Iteracja %d: zrelaksowano %d→%d, koszt=%d\n",
                    step.iteration, activeEdgeHighlight.source.id, activeEdgeHighlight.target.id, activeEdgeHighlight.weight));
        } else {
            logArea.appendText(String.format("Iteracja %d: sprawdzono %d→%d (brak relaksacji)\n",
                    step.iteration, activeEdgeHighlight.source.id, activeEdgeHighlight.target.id));
            activeEdgeHighlight.restoreAfterHighlight();
            activeEdgeHighlight = null;
        }
        updateStatus(String.format("Iteracja %d, krawędź %d→%d", step.iteration,
                step.edge.source.id, step.edge.target.id));
    }

    private void finishVisualization(BellmanFordResult result) {
        if (activeEdgeHighlight != null) {
            activeEdgeHighlight.restoreAfterHighlight();
            activeEdgeHighlight = null;
        }
        if (result.negativeCycle) {
            result.cycleEdges.forEach(GraphEdge::setErrorStyle);
            updateStatus("Wykryto ujemny cykl! Algorytm nie jest deterministyczny.");
        } else {
            // Podświetl drzewo najkrótszych ścieżek
            Set<GraphEdge> treeEdges = new HashSet<>();
            for (Map.Entry<Integer, Integer> entry : result.predecessor.entrySet()) {
                GraphNode target = nodes.get(entry.getKey());
                GraphNode source = nodes.get(entry.getValue());
                edges.stream()
                        .filter(e -> e.source == source && e.target == target)
                        .findFirst()
                        .ifPresent(treeEdges::add);
            }
            for (GraphEdge edge : edges) {
                if (treeEdges.contains(edge)) edge.setSuccessStyle();
                else edge.setDefaultStyle();
            }
            updateStatus("Bellman-Ford zakończony. Wyniki zostały obliczone i wyróżnione.");
        }
        graphPane.setDisable(false);
    }

    private void resetVisualization() {
        if (algorithmTimeline != null) algorithmTimeline.stop();
        if (activeEdgeHighlight != null) activeEdgeHighlight.restoreAfterHighlight();
        activeEdgeHighlight = null;
        edges.forEach(GraphEdge::setDefaultStyle);
        nodes.values().forEach(n -> n.updateDistance(Double.POSITIVE_INFINITY));
        String selected = sourceCombo.getValue();
        if (selected != null && !selected.isBlank()) {
            try {
                int src = Integer.parseInt(selected);
                if (nodes.containsKey(src)) nodes.get(src).updateDistance(0.0);
            } catch (NumberFormatException ignored) {}
        }
        logArea.clear();
        updateStatus("Wizualizacja zresetowana. Możesz edytować graf.");
    }

    private void clearGraph() {
        if (algorithmTimeline != null) algorithmTimeline.stop();
        edgeLayer.getChildren().clear();
        nodeLayer.getChildren().clear();
        nodes.clear();
        edges.clear();
        nextNodeId = 1;
        updateSourceCombo();
        logArea.clear();
        updateStatus("Graf wyczyszczony.");
    }

    // ---------------------------- KLASY WEWNĘTRZNE: WĘZEŁ, KRAWĘDŹ, KROK WIZUALIZACJI ----------------------------
    private class GraphNode {
        final int id;
        final StackPane view;
        final Label idLabel;
        final Label distanceLabel;
        final Circle circle;
        double x, y;
        double distance = Double.POSITIVE_INFINITY;
        private double pressX, pressY;

        GraphNode(int id, double centerX, double centerY) {
            this.id = id;
            this.x = clamp(centerX, 40, graphPane.getWidth() - 40);
            this.y = clamp(centerY, 40, graphPane.getHeight() - 40);

            circle = new Circle(30, Color.WHITE);
            circle.setStroke(Color.DARKBLUE);
            circle.setStrokeWidth(3);
            idLabel = new Label(String.valueOf(id));
            idLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
            distanceLabel = new Label("∞");
            distanceLabel.setFont(Font.font(11));

            view = new StackPane(circle, new VBox(2, idLabel, distanceLabel));
            view.setAlignment(javafx.geometry.Pos.CENTER);
            view.setPrefSize(60, 60);
            view.setLayoutX(x - 30);
            view.setLayoutY(y - 30);

            // Obsługa zdarzeń myszy
            view.setOnMousePressed(e -> {
                if (e.getButton() != MouseButton.PRIMARY) return;
                if (currentMode == Mode.ADD_EDGE) {
                    if (edgeSourceNode == null) {
                        highlightSelectedSource(this);
                        updateStatus("Zaznaczono źródło " + id + ". Teraz kliknij węzeł docelowy.");
                    } else if (edgeSourceNode == this) {
                        highlightSelectedSource(null);
                        updateStatus("Anulowano wybór źródła.");
                    } else {
                        try {
                            int w = Integer.parseInt(weightField.getText().trim());
                            addEdge(edgeSourceNode, this, w);
                        } catch (NumberFormatException ex) {
                            updateStatus("Wpisz poprawną wagę krawędzi.");
                        }
                    }
                    e.consume();
                    return;
                }
                if (currentMode == Mode.DELETE) {
                    removeNode(this);
                    e.consume();
                    return;
                }
                // Przeciąganie
                Point2D local = graphPane.sceneToLocal(e.getSceneX(), e.getSceneY());
                pressX = local.getX();
                pressY = local.getY();
                e.consume();
            });

            view.setOnMouseDragged(e -> {
                if (currentMode == Mode.ADD_EDGE || currentMode == Mode.DELETE) return;
                if (e.getButton() != MouseButton.PRIMARY) return;
                Point2D local = graphPane.sceneToLocal(e.getSceneX(), e.getSceneY());
                double newX = local.getX() - pressX + x;
                double newY = local.getY() - pressY + y;
                newX = clamp(newX, 30, graphPane.getWidth() - 30);
                newY = clamp(newY, 30, graphPane.getHeight() - 30);
                x = newX;
                y = newY;
                view.setLayoutX(x - 30);
                view.setLayoutY(y - 30);
                updateEdges();
                e.consume();
            });
        }

        private double clamp(double val, double min, double max) {
            return Math.max(min, Math.min(max, val));
        }

        boolean contains(Point2D point) {
            return point.distance(x, y) <= 30;
        }

        void updateDistance(double value) {
            this.distance = value;
            distanceLabel.setText(value == Double.POSITIVE_INFINITY ? "∞" : String.format("%.0f", value));
        }

        void setSelected(boolean selected) {
            circle.setStroke(selected ? Color.ORANGE : Color.DARKBLUE);
        }

        void updateEdges() {
            edges.stream().filter(e -> e.source == this || e.target == this).forEach(GraphEdge::updatePosition);
        }
    }

    private class GraphEdge {
        final GraphNode source;
        final GraphNode target;
        int weight;                   // zmieniana waga
        final Group view;
        final Line line;
        final Polygon arrow;
        final Label text;
        private boolean relaxed = false;

        GraphEdge(GraphNode source, GraphNode target, int weight) {
            this.source = source;
            this.target = target;
            this.weight = weight;
            line = new Line();
            line.setStrokeWidth(2);
            line.setStroke(Color.GRAY);
            arrow = new Polygon(-6, -4, 0, 0, -6, 4);
            arrow.setFill(Color.GRAY);
            text = new Label(String.valueOf(weight));
            text.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-border-color: #666; -fx-padding: 2;");
            view = new Group(line, arrow, text);

            // Kliknięcie prawym przyciskiem na widok krawędzi – zmiana wagi
            view.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.SECONDARY) {
                    handleEdgeRightClick(this);
                    e.consume();
                }
            });
        }

        void updatePosition() {
            Point2D from = new Point2D(source.x, source.y);
            Point2D to = new Point2D(target.x, target.y);
            Point2D dir = to.subtract(from);
            double dist = dir.magnitude();
            if (dist < 1) dist = 1;
            Point2D unit = dir.normalize();
            Point2D start = from.add(unit.multiply(30));
            Point2D end = to.subtract(unit.multiply(30));

            line.setStartX(start.getX());
            line.setStartY(start.getY());
            line.setEndX(end.getX());
            line.setEndY(end.getY());

            arrow.setLayoutX(end.getX());
            arrow.setLayoutY(end.getY());
            double angle = Math.toDegrees(Math.atan2(dir.getY(), dir.getX()));
            arrow.setRotate(angle);

            text.setLayoutX((start.getX() + end.getX()) / 2 - 14);
            text.setLayoutY((start.getY() + end.getY()) / 2 - 16);
        }

        void setDefaultStyle() {
            relaxed = false;
            line.setStroke(Color.GRAY);
            arrow.setFill(Color.GRAY);
            line.setOpacity(0.8);
        }

        void setSuccessStyle() {
            line.setStroke(Color.GREEN);
            arrow.setFill(Color.GREEN);
            line.setOpacity(1.0);
        }

        void setErrorStyle() {
            line.setStroke(Color.CRIMSON);
            arrow.setFill(Color.CRIMSON);
            line.setOpacity(1.0);
        }

        void highlightActive() {
            line.setStroke(Color.ORANGE);
            arrow.setFill(Color.ORANGE);
            line.setOpacity(1.0);
        }

        void markRelaxed() {
            relaxed = true;
            line.setStroke(Color.DODGERBLUE);
            arrow.setFill(Color.DODGERBLUE);
        }

        void restoreAfterHighlight() {
            if (relaxed) setSuccessStyle();
            else setDefaultStyle();
        }
    }

    private static class BFVisualStep {
        final GraphEdge edge;
        final boolean relax;
        final int iteration;
        BFVisualStep(GraphEdge edge, boolean relax, int iteration) {
            this.edge = edge;
            this.relax = relax;
            this.iteration = iteration;
        }
    }

    private record BellmanFordResult(int sourceId, Map<Integer, Double> distances,
                                     Map<Integer, Integer> predecessor,
                                     List<BFVisualStep> steps, boolean negativeCycle,
                                     Set<GraphEdge> cycleEdges) {}
}