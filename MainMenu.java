import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class MainMenu {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n=== Main Menu ===");
            System.out.println("1. Run Calculator Program");
            System.out.println("2. Run JDBC CRUD Operations Program");
            System.out.println("3. Run JavaFX Oracle Program");
            System.out.println("4. Exit");
            System.out.print("Enter your choice: ");
            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    runJarProgram("D:\\java projects\\calculator123.jar");
                    break;
                case "2":
                    runWithDependencies(
                        "D:\\java projects\\jdbcoracle.jar",
                        "jdbcoracle111", // Your JDBC program's main class (adjust if in package)
                        "C:\\Users\\ASUS\\Downloads\\ojdbc17.jar"
                    );
                    break;
                case "3":
                    runJavaFXProgram(
                        "D:\\java projects\\javafxoracle.jar",
                        "javafxoracle2", // Your JavaFX main class (adjust if in package)
                        "C:\\Users\\ASUS\\Downloads\\javafx-sdk-17.0.15\\lib",
                        "C:\\Users\\ASUS\\Downloads\\ojdbc17.jar" // Include JDBC jar here
                    );
                    break;
                case "4":
                    System.out.println("Exiting...");
                    scanner.close();
                    return;
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }

    private static void runJarProgram(String jarPath) {
        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            System.out.println("JAR file not found: " + jarPath);
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-jar", jarPath);
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println("Error running program: " + e.getMessage());
        }
    }

    private static void runWithDependencies(String jarPath, String mainClass, String dependencyJar) {
        File jarFile = new File(jarPath);
        File depFile = new File(dependencyJar);
        if (!jarFile.exists() || !depFile.exists()) {
            System.out.println("Required JAR files not found.");
            return;
        }

        try {
            String classpath = jarPath + ";" + dependencyJar;  // Use ":" on Linux/macOS
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", classpath, mainClass);
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println("Error running program with dependencies: " + e.getMessage());
        }
    }

    private static void runJavaFXProgram(String jarPath, String mainClass, String javafxLib, String jdbcJar) {
        File jarFile = new File(jarPath);
        File libDir = new File(javafxLib);
        File jdbcFile = new File(jdbcJar);
        if (!jarFile.exists() || !libDir.exists() || !jdbcFile.exists()) {
            System.out.println("JavaFX JAR, library path, or JDBC jar not found.");
            return;
        }

        try {
            String classpath = jarPath + ";" + jdbcJar; // Use ":" on Linux/macOS
            
            ProcessBuilder pb = new ProcessBuilder(
                "java",
                "--module-path", javafxLib,
                "--add-modules", "javafx.controls,javafx.fxml",
                "-cp", classpath,
                mainClass
            );
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println("Error running JavaFX program: " + e.getMessage());
        }
    }
}
