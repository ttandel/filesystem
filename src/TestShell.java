package filesystem;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;

public class TestShell
{
    public static String filesPath = "D:/";
    public static String inputFileName = "input.txt";
    public static String outputFileName = "91309435.txt";

    private FileInputStream inputStream;
    private File outputFile;
    private PrintWriter writer;

    private FileSystem fs;

    public void handleOperations(String line)
    {
        String[] commandTokens = line.split("\\s+");
        String arg1 = "";
        String arg2 = "";
        String arg3 = "";
        if (commandTokens.length >= 2)
            arg1 = commandTokens[1];
        if (commandTokens.length >= 3)
            arg2 = commandTokens[2];
        if (commandTokens.length >= 4)
            arg3 = commandTokens[3];

        switch (commandTokens[0])
        {
            case "cr": {
                String result = fs.create(arg1);
                writer.write(result);
                writer.write("\n");
                System.out.println(result);
                break;
            }
            case "de": {
                String result = fs.destroy(arg1);
                writer.write(result);
                writer.write("\n");
                System.out.println(result);
                break;
            }
            case "op": {
                String result = fs.open(arg1);
                writer.write(result);
                writer.write("\n");
                System.out.println(result);
                break;
            }
            case "cl": {
                int oftIndex;
                if (arg1 == "")
                    oftIndex = -1;
                else
                    oftIndex = Integer.parseInt(arg1);
                String result = fs.close(oftIndex);
                writer.write(result);
                writer.write("\n");
                System.out.println(result);
                break;
            }
            case "rd": {
                int oftIndex;
                if(arg1 == "")
                    oftIndex = -1;
                else
                    oftIndex = Integer.parseInt(arg1);
                int count;
                if (arg2 == "")
                    count = 0;
                else
                    count = Integer.parseInt(arg2);
                byte[] mem_area = new byte[count];
                String result = fs.read(oftIndex, mem_area, count);
                writer.write(result);
                writer.write("\n");
                System.out.println(result);
                break;
            }
            case "wr": {

                int oftIndex;
                if(arg1 == "")
                    oftIndex = -1;
                else
                    oftIndex = Integer.parseInt(arg1);
                String s = arg2;
                int count;
                if (arg3 == "")
                    count = 0;
                else
                    count = Integer.parseInt(arg3);
                byte[] mem_area = new byte[count];
                Arrays.fill(mem_area, s.getBytes()[0]);
                String result = fs.write(oftIndex, mem_area, count);
                writer.write(result);
                writer.write("\n");
                System.out.println(result);
                break;
            }
            case "sk": {

                int oftIndex;
                if (arg1 == "")
                    oftIndex = -1;
                else
                    oftIndex = Integer.parseInt(arg1);
                int pos;
                if (arg2 == "")
                    pos = -1;
                else
                    pos = Integer.parseInt(arg2);
                String result = fs.lseek(oftIndex, pos);
                writer.write(result);
                writer.write("\n");
                System.out.println(result);
                break;
            }
            case "dr": {
                String result = fs.directory();
                writer.write(result);
                writer.write("\n");
                System.out.println(result);
                break;
            }
            case "in": {
                String result = fs.initialize(arg1);
                writer.write(result);
                writer.write("\n");
                System.out.println(result);
                break;
            }
            case "sv": {
                String result = fs.save(arg1);
                writer.write(result);
                writer.write("\n");
                System.out.println(result);
                break;
            }
            default:
                writer.write("\n");
        }
    }

    public void run() {
        try {
            inputStream = new FileInputStream(filesPath + inputFileName);
            outputFile = new File(filesPath + outputFileName);
            writer = new PrintWriter(outputFile);

            this.fs = new FileSystem();

            try (Stream<String> stream = Files.lines(Paths.get(filesPath + inputFileName)))
            {
                stream.forEach(this::handleOperations);

            } catch (IOException e) {
                System.out.println("I/O Error");
            }
            inputStream.close();
            writer.close();
        } catch (FileNotFoundException e) {
            System.out.println("File(s) not found");
        } catch (IOException e) {
            System.out.println("I/O Error");
        }
    }

    public static void main(String[] args) {
        new TestShell().run();
    }
}
