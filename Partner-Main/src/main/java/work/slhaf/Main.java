package work.slhaf;

import work.slhaf.partner.Agent;

import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        Agent.initialize();
        Scanner scanner = new Scanner(System.in);
        while (!scanner.nextLine().equals("exit")) ;
    }
}