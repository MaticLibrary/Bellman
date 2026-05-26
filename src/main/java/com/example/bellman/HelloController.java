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

public class HelloController implements Initializable {

    @FXML private Pane graphPane;
    @FXML private ComboBox<String> sourceCombo;
    @FXML private ComboBox<String> targetCombo;
    @FXML private ComboBox<String> pathTypeCombo;
    @FXML private CheckBox directedGraphCheckbox;
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

    private enum Mode { ADD_NODE, ADD_EDGE, DELETE, NONE }
    private Mode currentMode = Mode.ADD_NODE;
    private Group edgeLayer;
    private Group nodeLayer;

    private int nextNodeId = 1;
    private final Map<Integer, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private GraphNode edgeSourceNode;
    private Timeline algorithmTimeline;
    private GraphEdge activeEdgeHighlight;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        edgeLayer = new Group();
        nodeLayer = new Group();
        graphPane.getChildren().addAll(edgeLayer, nodeLayer);

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
        pathTypeCombo.getItems().setAll("Najkrótsza (min)", "Najdłuższa (max)");
        pathTypeCombo.setValue("Najkrótsza (min)");
        directedGraphCheckbox.setSelected(false); // domyślnie nieskierowany
        switchMode(Mode.ADD_NODE);
        updateNodeCombos();
        updateStatus("Tryb dodawania węzłów: kliknij w puste miejsce. Graf jest NIESKIEROWANY (możesz zmienić).");
    }

    // ---------------------------- OBSŁUGA PRZYCISKÓW ----------------------------
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

    @FXML private void onRunAlgorithm()   { startBellmanFord(); }
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

    // ---------------------------- OPERACJE NA GRAFIE ----------------------------
    private void createNode(double x, double y) {
        int id = nextNodeId++;
        GraphNode node = new GraphNode(id, x, y);
        nodes.put(id, node);
        nodeLayer.getChildren().add(node.view);
        updateNodeCombos();
        updateStatus("Dodano węzeł " + id + ". Możesz go przeciągnąć lub dodać krawędź.");
    }

    /**
     * Dodaje krawędź (lub dwie, jeśli graf nieskierowany).
     * Dla nieskierowanego dodaje parę (source->target) i (target->source).
     */
    private void addEdge(GraphNode source, GraphNode target, int weight) {
        // Sprawdź, czy krawędź już istnieje w danym kierunku
        if (edges.stream().anyMatch(e -> e.source == source && e.target == target)) {
            updateStatus("Krawędź już istnieje: " + source.id + " -> " + target.id);
            return;
        }

        // Dodaj krawędź główną
        GraphEdge edge = new GraphEdge(source, target, weight);
        edges.add(edge);
        edgeLayer.getChildren().add(edge.view);
        edge.updatePosition();

        // Dla grafu nieskierowanego dodaj krawędź powrotną (jeśli jeszcze nie istnieje)
        if (!directedGraphCheckbox.isSelected()) {
            if (edges.stream().noneMatch(e -> e.source == target && e.target == source)) {
                GraphEdge backEdge = new GraphEdge(target, source, weight);
                edges.add(backEdge);
                edgeLayer.getChildren().add(backEdge.view);
                backEdge.updatePosition();
                updateStatus("Dodano krawędź dwukierunkową " + source.id + " ↔ " + target.id + ", waga = " + weight);
            } else {
                updateStatus("Dodano krawędź " + source.id + " → " + target.id + " (powrotna już istniała)");
            }
        } else {
            updateStatus("Dodano krawędź skierowaną " + source.id + " → " + target.id + ", waga = " + weight);
        }

        if (currentMode == Mode.ADD_EDGE) {
            edgeSourceNode = null;
            highlightSelectedSource(null);
        }
    }

    /** Obsługa zmiany wagi – dla nieskierowanego również zmienia wagę krawędzi przeciwnej */
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
                // Jeśli graf nieskierowany, znajdź krawędź przeciwną i zaktualizuj jej wagę
                if (!directedGraphCheckbox.isSelected()) {
                    edges.stream()
                            .filter(e -> e.source == edge.target && e.target == edge.source)
                            .findFirst()
                            .ifPresent(backEdge -> {
                                backEdge.weight = newWeight;
                                backEdge.text.setText(String.valueOf(newWeight));
                                backEdge.setDefaultStyle();
                            });
                }
                updateStatus("Zmieniono wagę krawędzi " + edge.source.id + "→" + edge.target.id + " na " + newWeight);
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
        updateNodeCombos();
        updateStatus("Usunięto węzeł " + node.id + " wraz z powiązanymi krawędziami.");
    }

    private void updateNodeCombos() {
        List<String> ids = nodes.keySet().stream().map(String::valueOf).toList();
        sourceCombo.getItems().setAll(ids);
        targetCombo.getItems().setAll(ids);
        if (!ids.isEmpty()) {
            if (!ids.contains(sourceCombo.getValue())) sourceCombo.setValue(ids.get(0));
            if (!ids.contains(targetCombo.getValue())) targetCombo.setValue(ids.get(0));
        } else {
            sourceCombo.setValue(null);
            targetCombo.setValue(null);
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

        Integer targetId = null;
        String targetValue = targetCombo.getValue();
        if (targetValue != null && !targetValue.isBlank()) {
            targetId = Integer.parseInt(targetValue);
            if (!nodes.containsKey(targetId)) targetId = null;
        }

        boolean findLongest = "Najdłuższa (max)".equals(pathTypeCombo.getValue());

        resetVisualization();
        BellmanFordResult result = computeBellmanFord(sourceId, findLongest);
        if (result.steps.isEmpty()) {
            updateStatus("Brak krawędzi do przetworzenia.");
            return;
        }
        if (result.negativeCycle) {
            logArea.appendText("UWAGA: wykryto " + (findLongest ? "cykl dodatni" : "ujemny cykl") + "! Wyniki mogą być niepoprawne.\n");
        }
        runVisualization(result, targetId, findLongest);
    }

    private BellmanFordResult computeBellmanFord(int sourceId, boolean findLongest) {
        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> pred = new HashMap<>();
        double INF = findLongest ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (int id : nodes.keySet()) dist.put(id, INF);
        dist.put(sourceId, 0.0);

        List<BFVisualStep> steps = new ArrayList<>();
        int n = nodes.size();

        for (int i = 1; i < n; i++) {
            boolean changed = false;
            for (GraphEdge edge : edges) {
                double uDist = dist.get(edge.source.id);
                double vDist = dist.get(edge.target.id);
                boolean relax;
                if (findLongest) {
                    relax = (uDist != Double.NEGATIVE_INFINITY && uDist + edge.weight > vDist);
                    if (relax) {
                        dist.put(edge.target.id, uDist + edge.weight);
                        pred.put(edge.target.id, edge.source.id);
                        changed = true;
                    }
                } else {
                    relax = (uDist != Double.POSITIVE_INFINITY && uDist + edge.weight < vDist);
                    if (relax) {
                        dist.put(edge.target.id, uDist + edge.weight);
                        pred.put(edge.target.id, edge.source.id);
                        changed = true;
                    }
                }
                steps.add(new BFVisualStep(edge, relax, i));
            }
            if (!changed) break;
        }

        boolean negativeCycle = false;
        Set<GraphEdge> cycleEdges = new HashSet<>();
        for (GraphEdge edge : edges) {
            double uDist = dist.get(edge.source.id);
            double vDist = dist.get(edge.target.id);
            if (findLongest) {
                if (uDist != Double.NEGATIVE_INFINITY && uDist + edge.weight > vDist) {
                    negativeCycle = true;
                    cycleEdges.add(edge);
                }
            } else {
                if (uDist != Double.POSITIVE_INFINITY && uDist + edge.weight < vDist) {
                    negativeCycle = true;
                    cycleEdges.add(edge);
                }
            }
        }

        return new BellmanFordResult(sourceId, dist, pred, steps, negativeCycle, cycleEdges, findLongest);
    }

    private void runVisualization(BellmanFordResult result, Integer targetId, boolean findLongest) {
        logArea.clear();
        nodes.values().forEach(n -> n.updateDistance(findLongest ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY));
        nodes.get(result.sourceId).updateDistance(0.0);
        edges.forEach(GraphEdge::setDefaultStyle);
        activeEdgeHighlight = null;

        algorithmTimeline = new Timeline();
        graphPane.setDisable(true);

        int stepIndex = 0;
        for (BFVisualStep step : result.steps) {
            double time = stepIndex * 320.0;
            algorithmTimeline.getKeyFrames().add(new KeyFrame(Duration.millis(time), e -> applyStep(step, findLongest)));
            stepIndex++;
        }
        algorithmTimeline.getKeyFrames().add(new KeyFrame(Duration.millis(stepIndex * 320.0 + 300), e -> finishVisualization(result, targetId)));
        algorithmTimeline.play();
    }

    private void applyStep(BFVisualStep step, boolean findLongest) {
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

    private void finishVisualization(BellmanFordResult result, Integer targetId) {
        if (activeEdgeHighlight != null) {
            activeEdgeHighlight.restoreAfterHighlight();
            activeEdgeHighlight = null;
        }

        if (result.negativeCycle) {
            result.cycleEdges.forEach(GraphEdge::setErrorStyle);
            updateStatus("Wykryto " + (result.findLongest ? "cykl dodatni" : "ujemny cykl") + "!");
        } else {
            // Podświetlenie drzewa najtańszych/najdroższych ścieżek
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

            // Wyświetlenie ścieżki do wybranego węzła docelowego
            if (targetId != null && nodes.containsKey(targetId)) {
                Double distance = result.distances.get(targetId);
                boolean reachable = result.findLongest ?
                        distance != Double.NEGATIVE_INFINITY :
                        distance != Double.POSITIVE_INFINITY;
                if (reachable) {
                    List<Integer> path = reconstructPath(result.predecessor, result.sourceId, targetId);
                    String pathStr = path.stream().map(String::valueOf).reduce((a,b) -> a + " → " + b).orElse("");
                    logArea.appendText("\n=== ŚCIEŻKA " + (result.findLongest ? "NAJDROŻSZA" : "NAJTAŃSZA") + " ===\n");
                    logArea.appendText("Z węzła " + result.sourceId + " do " + targetId + ": " + pathStr + "\n");
                    logArea.appendText("Całkowity koszt: " + (result.findLongest ? distance : distance) + "\n");
                } else {
                    logArea.appendText("\nWęzeł docelowy " + targetId + " jest nieosiągalny.\n");
                }
            }
            updateStatus("Bellman-Ford zakończony. Wyniki obliczone.");
        }
        graphPane.setDisable(false);
    }

    private List<Integer> reconstructPath(Map<Integer, Integer> predecessor, int source, int target) {
        List<Integer> path = new ArrayList<>();
        Integer cur = target;
        while (cur != null && cur != source) {
            path.add(0, cur);
            cur = predecessor.get(cur);
        }
        if (cur == source) path.add(0, source);
        else path.clear();
        return path;
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
        updateStatus("Wizualizacja zresetowana.");
    }

    private void clearGraph() {
        if (algorithmTimeline != null) algorithmTimeline.stop();
        edgeLayer.getChildren().clear();
        nodeLayer.getChildren().clear();
        nodes.clear();
        edges.clear();
        nextNodeId = 1;
        updateNodeCombos();
        logArea.clear();
        updateStatus("Graf wyczyszczony.");
    }

    // ---------------------------- KLASY WEWNĘTRZNE ----------------------------
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
            String text = (value == Double.POSITIVE_INFINITY) ? "∞" :
                    (value == Double.NEGATIVE_INFINITY) ? "-∞" :
                            String.format("%.0f", value);
            distanceLabel.setText(text);
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
        int weight;
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
                                     Set<GraphEdge> cycleEdges, boolean findLongest) {}
}
