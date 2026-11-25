import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente grafico (JavaFX 21) del tres en raya.
 * Habla el mismo protocolo que Client.java; el Server no cambia.
 *
 * - Un hilo lee del servidor y pasa cada linea a la interfaz con Platform.runLater.
 * - El historial se construye con cada "OK <tablero>" recibido y se puede
 *   revisar (jugada a jugada) cuando la partida termina.
 */
public class ClientFX extends Application {

    private Socket socket;
    private PrintWriter out;
    private String miNombre;
    private char miMarca = '?';
    private boolean partidaTerminada = false;
    private boolean cerrando = false;
    private boolean rechazado = false;

    // Interfaz
    private final Button[][] celdas = new Button[3][3];
    private final Label estado = new Label("Conectando...");
    private final Button btnRevancha = new Button("Revancha");
    private final Button btnSalir = new Button("Salir");

    // Historial y revision
    private final List<String> historial = new ArrayList<>();
    private int indiceActual = -1;
    private final Button btnAnterior = new Button("<");
    private final Button btnSiguiente = new Button(">");
    private final Label lblJugada = new Label();
    private HBox panelRevision;

    public static void main(String[] args) {
        launch(args);
    }

    // ------------------------------------------------------------------
    // Pantalla de conexion
    // ------------------------------------------------------------------
    @Override
    public void start(Stage stage) {
        TextField host = new TextField("localhost");
        TextField puerto = new TextField("5050");
        TextField nombre = new TextField();
        nombre.setPromptText("Tu nombre");
        Label error = new Label();
        Button conectar = new Button("Conectar");

        conectar.setOnAction(e -> {
            if (nombre.getText().trim().isEmpty()) {
                error.setText("Escribe un nombre");
                return;
            }
            try {
                socket = new Socket(host.getText().trim(), Integer.parseInt(puerto.getText().trim()));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"));
                miNombre = nombre.getText().trim().replace(" ", "_");
                out.println("PLAY " + miNombre);

                stage.setScene(new Scene(construirTablero(), 340, 480));

                Thread lector = new Thread(() -> escuchar(in));
                lector.setDaemon(true);
                lector.start();
            } catch (Exception ex) {
                error.setText("No se pudo conectar: " + ex.getMessage());
            }
        });

        VBox login = new VBox(8, new Label("Servidor"), host, new Label("Puerto"), puerto,
                new Label("Nombre"), nombre, conectar, error);
        login.setPadding(new Insets(20));
        stage.setScene(new Scene(login, 300, 340));
        stage.setTitle("Tres en raya");
        stage.setOnCloseRequest(e -> cerrar());
        stage.show();
    }

    // ------------------------------------------------------------------
    // Tablero y controles
    // ------------------------------------------------------------------
    private Parent construirTablero() {
        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(5);
        grid.setAlignment(Pos.CENTER);
        for (int f = 0; f < 3; f++) {
            for (int c = 0; c < 3; c++) {
                Button b = new Button();
                b.setPrefSize(90, 90);
                b.setStyle("-fx-font-size: 32px;");
                final int fila = f;
                final int col = c;
                b.setOnAction(e -> {
                    if (!partidaTerminada) {
                        out.println("MOVE " + (fila + 1) + "," + (col + 1));
                    }
                });
                celdas[f][c] = b;
                grid.add(b, c, f);
            }
        }

        btnRevancha.setVisible(false);
        btnRevancha.setOnAction(e -> out.println("REMATCH"));
        btnSalir.setOnAction(e -> cerrar());

        btnAnterior.setOnAction(e -> mostrarRevision(indiceActual - 1));
        btnSiguiente.setOnAction(e -> mostrarRevision(indiceActual + 1));
        panelRevision = new HBox(10, btnAnterior, lblJugada, btnSiguiente);
        panelRevision.setAlignment(Pos.CENTER);
        panelRevision.setVisible(false);
        panelRevision.setManaged(false);

        HBox botones = new HBox(10, btnRevancha, btnSalir);
        botones.setAlignment(Pos.CENTER);

        VBox root = new VBox(12, estado, grid, panelRevision, botones);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(15));
        return root;
    }

    private void pintar(String tablero) {
        for (int k = 0; k < 9; k++) {
            char c = tablero.charAt(k);
            celdas[k / 3][k % 3].setText(c == '.' ? "" : String.valueOf(c));
        }
    }

    // ------------------------------------------------------------------
    // Red: hilo lector -> interfaz
    // ------------------------------------------------------------------
    private void escuchar(BufferedReader in) {
        try {
            String linea;
            while ((linea = in.readLine()) != null) {
                final String l = linea;
                Platform.runLater(() -> procesar(l));
            }
        } catch (IOException e) {
            // conexion cerrada
        }
        Platform.runLater(() -> {
            if (!cerrando && !rechazado) {
                estado.setText("Conexion con el servidor cerrada");
                partidaTerminada = true;
            }
        });
    }

    /** Se ejecuta siempre en el hilo de JavaFX. */
    private void procesar(String linea) {
        String[] p = linea.split(" ");
        switch (p[0]) {
            case "FULL":
                rechazado = true;
                partidaTerminada = true;
                estado.setText("Partida llena: no puedes entrar");
                break;
            case "WAIT":
                estado.setText("Esperando al otro jugador...");
                break;
            case "START":
                miMarca = p[1].charAt(0);
                partidaTerminada = false;
                historial.clear();
                ocultarRevision();
                btnRevancha.setVisible(false);
                btnRevancha.setDisable(false);
                pintar(".........");
                estado.setText("Eres '" + miMarca + "' contra " + p[2]);
                break;
            case "OK":
                historial.add(p[1]);
                pintar(p[1]);
                break;
            case "TURN":
                estado.setText(p[1].charAt(0) == miMarca ? "Tu turno" : "Turno del rival");
                break;
            case "DENIED":
                estado.setText("Jugada rechazada: " + linea.substring(7));
                break;
            case "END":
                partidaTerminada = true;
                if (p[1].equals("DRAW")) {
                    estado.setText("Empate");
                } else if (p.length == 5) {
                    estado.setText("El rival ha abandonado: ganas tu");
                } else if (p[2].charAt(0) == miMarca) {
                    estado.setText("Has ganado!");
                } else {
                    estado.setText("Has perdido (gana " + p[3] + ")");
                }
                btnRevancha.setVisible(true);
                guardarHistorial();
                activarRevision();
                break;
            case "REMATCH":
                estado.setText("El rival quiere revancha");
                break;
            case "LEFT":
                estado.setText("El rival se ha ido");
                btnRevancha.setDisable(true);
                break;
            case "ERROR":
                estado.setText("Error: " + linea.substring(6));
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------
    // Revision del historial
    // ------------------------------------------------------------------
    private void activarRevision() {
        if (historial.isEmpty()) {
            return;
        }
        panelRevision.setVisible(true);
        panelRevision.setManaged(true);
        mostrarRevision(historial.size() - 1);
    }

    private void ocultarRevision() {
        panelRevision.setVisible(false);
        panelRevision.setManaged(false);
    }

    private void mostrarRevision(int i) {
        if (i < 0 || i >= historial.size()) {
            return;
        }
        indiceActual = i;
        pintar(historial.get(i));
        btnAnterior.setDisable(i == 0);
        btnSiguiente.setDisable(i == historial.size() - 1);
        lblJugada.setText("Jugada " + (i + 1) + "/" + historial.size() + descripcion(i));
    }

    /** Compara con el tablero anterior para saber que casilla se jugo. */
    private String descripcion(int i) {
        String antes = (i == 0) ? "........." : historial.get(i - 1);
        String ahora = historial.get(i);
        for (int k = 0; k < 9; k++) {
            if (antes.charAt(k) != ahora.charAt(k)) {
                return ": " + ahora.charAt(k) + " en (" + (k / 3 + 1) + "," + (k % 3 + 1) + ")";
            }
        }
        return "";
    }

    private void guardarHistorial() {
        try {
            Files.write(Path.of("historial_" + miNombre + ".txt"), historial);
        } catch (IOException e) {
            // no es critico para la partida
        }
    }

    // ------------------------------------------------------------------
    // Cierre
    // ------------------------------------------------------------------
    private void cerrar() {
        cerrando = true;
        try {
            if (out != null) {
                out.println("BYE");
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
        Platform.exit();
    }
}
