import java.io.*;
import java.net.*;

/**
 * Cliente de consola del tres en raya.
 * Uso: java Client [host] [puerto] [nombre]
 *
 * Tiene dos hilos: el principal lee el teclado y envia las jugadas, y otro
 * (LectorServidor) escucha al servidor y escribe en pantalla lo que llega.
 */
public class Client {

    static char miMarca = '?';
    static volatile boolean saliendo = false;

    public static void main(String[] args) throws IOException {
        String host = "localhost";
        int puerto = 5050;
        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            puerto = Integer.parseInt(args[1]);
        }

        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        String nombre;
        if (args.length > 2) {
            nombre = args[2];
        } else {
            System.out.print("Tu nombre: ");
            nombre = teclado.readLine();
            if (nombre == null || nombre.trim().isEmpty()) {
                nombre = "jugador";
            }
        }

        Socket socket;
        try {
            socket = new Socket(host, puerto);
        } catch (IOException e) {
            System.out.println("No se pudo conectar con " + host + ":" + puerto);
            return;
        }

        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"));
        PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

        out.println("PLAY " + nombre);

        LectorServidor lector = new LectorServidor(in);
        lector.setDaemon(true);
        lector.start();

        // Bucle del teclado
        String linea;
        while ((linea = teclado.readLine()) != null) {
            linea = linea.trim();
            if (linea.equalsIgnoreCase("q")) {
                saliendo = true;
                out.println("BYE");
                break;
            } else if (linea.equalsIgnoreCase("r")) {
                out.println("REMATCH");
            } else if (linea.length() == 3 && (linea.charAt(1) == ',' || linea.charAt(1) == ' ')) {
                // "2,3" o "2 3"  ->  MOVE 2,3
                out.println("MOVE " + linea.charAt(0) + "," + linea.charAt(2));
            } else if (!linea.isEmpty()) {
                System.out.println("Comandos: fila,col (ej. 2,2) | r = revancha | q = salir");
            }
        }
        socket.close();
        System.out.println("Desconectado.");
    }

    // ------------------------------------------------------------------
    // Hilo que escucha al servidor
    // ------------------------------------------------------------------
    static class LectorServidor extends Thread {
        BufferedReader in;

        LectorServidor(BufferedReader in) {
            this.in = in;
        }

        public void run() {
            try {
                String linea;
                while ((linea = in.readLine()) != null) {
                    mostrar(linea);
                }
            } catch (IOException e) {
                // conexion cerrada
            }
            if (!saliendo) {
                System.out.println("El servidor ha cerrado la conexion.");
            }
            System.exit(0); // el hilo principal puede estar esperando el teclado
        }
    }

    static void mostrar(String linea) {
        String[] p = linea.split(" ");
        String tipo = p[0];

        if (tipo.equals("FULL")) {
            System.out.println("Partida llena: no puedes entrar.");
            saliendo = true;
        } else if (tipo.equals("WAIT")) {
            System.out.println("Esperando al otro jugador...");
        } else if (tipo.equals("START")) {
            miMarca = p[1].charAt(0);
            System.out.println("\n=== Empieza la partida ===");
            System.out.println("Eres '" + miMarca + "' y juegas contra " + p[2]);
        } else if (tipo.equals("OK")) {
            dibujarTablero(p[1]);
        } else if (tipo.equals("TURN")) {
            if (p[1].charAt(0) == miMarca) {
                System.out.println("Tu turno. Escribe fila,col (1-3), por ejemplo 2,2:");
            } else {
                System.out.println("Turno del rival (" + p[1] + ")...");
            }
        } else if (tipo.equals("DENIED")) {
            System.out.println("Jugada rechazada: " + linea.substring(7));
        } else if (tipo.equals("END")) {
            if (p[1].equals("DRAW")) {
                System.out.println("\n*** Empate ***");
            } else if (p.length == 5) {
                System.out.println("\n*** El rival ha abandonado: ganas tu ***");
            } else if (p[2].charAt(0) == miMarca) {
                System.out.println("\n*** Has ganado! ***");
            } else {
                System.out.println("\n*** Has perdido (gana " + p[3] + ") ***");
            }
            System.out.println("Escribe 'r' para pedir revancha o 'q' para salir.");
        } else if (tipo.equals("REMATCH")) {
            System.out.println("El rival quiere revancha. Escribe 'r' para aceptar o 'q' para salir.");
        } else if (tipo.equals("LEFT")) {
            System.out.println("El rival se ha ido. Escribe 'q' para salir.");
        } else if (tipo.equals("ERROR")) {
            System.out.println("Error del servidor: " + linea.substring(6));
        } else {
            System.out.println("Mensaje desconocido: " + linea);
        }
    }

    static void dibujarTablero(String t) {
        System.out.println();
        System.out.println("    1   2   3");
        for (int fila = 0; fila < 3; fila++) {
            String texto = " " + (fila + 1) + "  ";
            for (int col = 0; col < 3; col++) {
                char c = t.charAt(fila * 3 + col);
                if (c == '.') {
                    c = ' ';
                }
                texto = texto + c;
                if (col < 2) {
                    texto = texto + " | ";
                }
            }
            System.out.println(texto);
            if (fila < 2) {
                System.out.println("   ---+---+---");
            }
        }
        System.out.println();
    }
}