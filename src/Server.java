import java.io.*;
import java.net.*;

/**
 * Servidor del tres en raya (arbitro de la partida).
 * Solo admite dos jugadores. Uso: java Server [puerto]
 *
 * Cada cliente se atiende en su propio hilo (ClientHandler), pero todos
 * comparten el estado de la partida (tablero, turno...). Para que dos hilos
 * no lo toquen a la vez, los metodos que lo usan son "synchronized".
 */
public class Server {

    static final int PUERTO_POR_DEFECTO = 5050;

    // Estado de la partida (compartido por los dos hilos)
    static ClientHandler[] jugadores = new ClientHandler[2];
    static char[] tablero = new char[9];
    static char turno = 'X';
    static final int ESPERANDO = 0;
    static final int JUGANDO = 1;
    static final int TERMINADA = 2;
    static int estado = ESPERANDO;

    public static void main(String[] args) throws IOException {
        int puerto = PUERTO_POR_DEFECTO;
        if (args.length > 0) {
            puerto = Integer.parseInt(args[0]);
        }
        vaciarTablero();

        ServerSocket serverSocket = new ServerSocket(puerto);
        System.out.println("[servidor] Servidor escuchando en el puerto " + puerto);

        while (true) {
            Socket socket = serverSocket.accept();
            ClientHandler hilo = new ClientHandler(socket);
            hilo.setDaemon(true);
            hilo.start();
        }
    }

    // ------------------------------------------------------------------
    // Un hilo por cliente
    // ------------------------------------------------------------------
    static class ClientHandler extends Thread {
        Socket socket;
        PrintWriter out;
        String nombre;
        char marca;
        boolean dentro = false;       // true cuando ya esta sentado en la partida
        boolean quiereRevancha = false;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        // synchronized: dos hilos pueden escribirle al mismo jugador
        synchronized void enviar(String mensaje) {
            out.println(mensaje);
        }

        public void run() {
            try {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                // El primer mensaje tiene que ser PLAY <nombre> (15 s como maximo)
                socket.setSoTimeout(15000);
                String linea = in.readLine();
                if (linea == null) {
                    return;
                }
                String[] partes = linea.trim().split(" ", 2);
                if (!partes[0].equalsIgnoreCase("PLAY") || partes.length < 2
                        || partes[1].trim().isEmpty()) {
                    out.println("ERROR Se esperaba: PLAY <nombre>");
                    return;
                }
                socket.setSoTimeout(0);

                nombre = partes[1].trim().replace(" ", "_");
                if (nombre.length() > 20) {
                    nombre = nombre.substring(0, 20);
                }

                if (!entrar(this)) {
                    out.println("FULL");
                    socket.shutdownOutput();
                    return;
                }

                // Bucle principal: leer lo que manda el jugador
                while ((linea = in.readLine()) != null) {
                    boolean seguir = procesar(this, linea.trim());
                    if (!seguir) {
                        break; // BYE
                    }
                }
            } catch (IOException e) {
                // el cliente se ha caido: se trata igual que una desconexion
            } finally {
                if (dentro) {
                    salir(this);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Logica de la partida (todo synchronized)
    // ------------------------------------------------------------------

    static void vaciarTablero() {
        for (int i = 0; i < 9; i++) {
            tablero[i] = '.';
        }
        turno = 'X';
    }

    static int contarJugadores() {
        int n = 0;
        for (int i = 0; i < 2; i++) {
            if (jugadores[i] != null) {
                n++;
            }
        }
        return n;
    }

    static ClientHandler rivalDe(ClientHandler j) {
        if (jugadores[0] == j) {
            return jugadores[1];
        }
        return jugadores[0];
    }

    static void avisarATodos(String mensaje) {
        for (int i = 0; i < 2; i++) {
            if (jugadores[i] != null) {
                jugadores[i].enviar(mensaje);
            }
        }
    }

    /** Sienta al jugador. Devuelve false si la partida esta llena. */
    static synchronized boolean entrar(ClientHandler j) {
        if (contarJugadores() >= 2 || estado != ESPERANDO) {
            return false;
        }
        j.dentro = true;
        if (jugadores[0] == null) {
            jugadores[0] = j;
            j.marca = 'X';
            j.enviar("WAIT");
            System.out.println("[servidor] " + j.nombre + " conectado como X, esperando rival");
        } else {
            jugadores[1] = j;
            j.marca = 'O';
            System.out.println("[servidor] " + j.nombre + " conectado como O, empieza la partida");
            empezarPartida();
        }
        return true;
    }

    static void empezarPartida() {
        vaciarTablero();
        jugadores[0].quiereRevancha = false;
        jugadores[1].quiereRevancha = false;
        estado = JUGANDO;
        jugadores[0].enviar("START " + jugadores[0].marca + " " + jugadores[1].nombre);
        jugadores[1].enviar("START " + jugadores[1].marca + " " + jugadores[0].nombre);
        avisarATodos("TURN X");
    }

    /** Procesa una linea del cliente. Devuelve false si hay que cerrar la conexion. */
    static synchronized boolean procesar(ClientHandler j, String linea) {
        if (linea.isEmpty()) {
            return true;
        }
        String[] partes = linea.split(" ", 2);
        String comando = partes[0].toUpperCase();
        String argumento = "";
        if (partes.length > 1) {
            argumento = partes[1].trim();
        }

        if (comando.equals("MOVE")) {
            mover(j, argumento);
        } else if (comando.equals("REMATCH")) {
            revancha(j);
        } else if (comando.equals("BYE")) {
            return false;
        } else {
            j.enviar("ERROR Comando desconocido: " + comando);
        }
        return true;
    }

    static void mover(ClientHandler j, String argumento) {
        if (estado == ESPERANDO) {
            j.enviar("DENIED La partida aun no ha empezado");
            return;
        }
        if (estado == TERMINADA) {
            j.enviar("DENIED Partida terminada");
            return;
        }
        if (j.marca != turno) {
            j.enviar("DENIED No es tu turno");
            return;
        }

        // Leer fila y columna: "2,3"
        int fila;
        int col;
        try {
            String[] numeros = argumento.split(",");
            if (numeros.length != 2) {
                throw new NumberFormatException();
            }
            fila = Integer.parseInt(numeros[0].trim());
            col = Integer.parseInt(numeros[1].trim());
        } catch (NumberFormatException e) {
            j.enviar("DENIED Formato invalido, usa MOVE fila,col (1-3)");
            return;
        }
        if (fila < 1 || fila > 3 || col < 1 || col > 3) {
            j.enviar("DENIED Casilla fuera del tablero (fila y columna de 1 a 3)");
            return;
        }
        int pos = (fila - 1) * 3 + (col - 1);
        if (tablero[pos] != '.') {
            j.enviar("DENIED Casilla ocupada");
            return;
        }

        // Jugada valida
        tablero[pos] = j.marca;
        System.out.println("[servidor] " + j.nombre + " (" + j.marca + ") juega " + fila + "," + col);
        avisarATodos("OK " + new String(tablero));

        char ganador = buscarGanador();
        if (ganador != ' ') {
            estado = TERMINADA;
            avisarATodos("END WIN " + ganador + " " + j.nombre);
            System.out.println("[servidor] Fin de partida: gana " + j.nombre);
        } else if (tableroLleno()) {
            estado = TERMINADA;
            avisarATodos("END DRAW");
            System.out.println("[servidor] Fin de partida: empate");
        } else {
            if (turno == 'X') {
                turno = 'O';
            } else {
                turno = 'X';
            }
            avisarATodos("TURN " + turno);
        }
    }

    static void revancha(ClientHandler j) {
        if (estado != TERMINADA) {
            j.enviar("DENIED La revancha solo se pide al terminar la partida");
            return;
        }
        ClientHandler rival = rivalDe(j);
        if (rival == null) {
            j.enviar("DENIED El rival ya no esta conectado");
            return;
        }
        j.quiereRevancha = true;
        if (rival.quiereRevancha) {
            // Los dos quieren: se cambian las marcas para que empiece el otro
            for (int i = 0; i < 2; i++) {
                if (jugadores[i].marca == 'X') {
                    jugadores[i].marca = 'O';
                } else {
                    jugadores[i].marca = 'X';
                }
            }
            System.out.println("[servidor] Revancha aceptada por ambos");
            empezarPartida();
        } else {
            j.enviar("WAIT");
            rival.enviar("REMATCH");
        }
    }

    /** Se llama cuando un jugador se va (BYE, caida o error). */
    static synchronized void salir(ClientHandler j) {
        if (jugadores[0] == j) {
            jugadores[0] = null;
        } else if (jugadores[1] == j) {
            jugadores[1] = null;
        } else {
            return;
        }
        j.dentro = false;
        System.out.println("[servidor] " + j.nombre + " desconectado");

        ClientHandler otro = jugadores[0];
        if (otro == null) {
            otro = jugadores[1];
        }

        if (otro != null) {
            otro.quiereRevancha = false;
            if (estado == JUGANDO) {
                estado = TERMINADA;
                otro.enviar("END WIN " + otro.marca + " " + otro.nombre + " ABANDONO");
            } else if (estado == TERMINADA) {
                otro.enviar("LEFT");
            }
        } else {
            // No queda nadie: la sala vuelve a estar libre
            estado = ESPERANDO;
            vaciarTablero();
            System.out.println("[servidor] Sala vacia, lista para una nueva partida");
        }
    }

    // ------------------------------------------------------------------
    // Comprobaciones del tablero
    // ------------------------------------------------------------------

    static boolean tableroLleno() {
        for (int i = 0; i < 9; i++) {
            if (tablero[i] == '.') {
                return false;
            }
        }
        return true;
    }

    /** Devuelve 'X' u 'O' si hay tres en raya, o ' ' si nadie ha ganado. */
    static char buscarGanador() {
        int[][] lineas = {
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8},   // filas
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8},   // columnas
                {0, 4, 8}, {2, 4, 6}               // diagonales
        };
        for (int i = 0; i < lineas.length; i++) {
            char a = tablero[lineas[i][0]];
            char b = tablero[lineas[i][1]];
            char c = tablero[lineas[i][2]];
            if (a != '.' && a == b && b == c) {
                return a;
            }
        }
        return ' ';
    }
}