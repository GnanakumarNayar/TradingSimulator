package com.tradingsim;

import java.io.*;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * TradingSimulator - Eclipse-friendly single-file Java stock trading simulator
 *
 * To use in Eclipse:
 * 1. File → New → Java Project → enter project name (e.g. TradingSimulator)
 * 2. Right-click src → New → Package → enter: com.tradingsim
 * 3. Right-click package → New → Class → Name: TradingSimulator (ensure 'public static void main' is checked)
 * 4. Paste this file's contents into TradingSimulator.java and Save.

public class TradingSimulator {

    private final Market market = new Market();
    private User user;
    private final Scanner scanner = new Scanner(System.in);
    private final DecimalFormat moneyFmt = new DecimalFormat("#,##0.00");

    public static void main(String[] args) {
        new TradingSimulator().run();
    }

    private void run() {
        System.out.println("Welcome to the Mini Stock Trading Simulator!");
        System.out.print("Enter your name: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) name = "Trader";

        user = new User(name, 10000.0); // start with 10k cash
        market.seedSampleStocks();
        user.getPortfolio().recordHistory(LocalDateTime.now(), user.getPortfolio().getTotalValue(market));

        help();

        while (true) {
            System.out.print("
> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\s+");
            String cmd = parts[0].toLowerCase();
            try {
                switch (cmd) {
                    case "help": help(); break;
                    case "market": market.displayMarket(); break;
                    case "buy":
                        if (parts.length < 3) { System.out.println("Usage: buy SYMBOL QTY"); break; }
                        buy(parts[1].toUpperCase(), Integer.parseInt(parts[2]));
                        break;
                    case "sell":
                        if (parts.length < 3) { System.out.println("Usage: sell SYMBOL QTY"); break; }
                        sell(parts[1].toUpperCase(), Integer.parseInt(parts[2]));
                        break;
                    case "portfolio": displayPortfolio(); break;
                    case "history": displayHistory(); break;
                    case "simulate":
                        int steps = 1;
                        if (parts.length >= 2) steps = Integer.parseInt(parts[1]);
                        simulate(steps);
                        break;
                    case "save":
                        if (parts.length < 2) { System.out.println("Usage: save filename"); break; }
                        Persistence.saveUser(user, parts[1]);
                        break;
                    case "load":
                        if (parts.length < 2) { System.out.println("Usage: load filename"); break; }
                        User loaded = Persistence.loadUser(parts[1]);
                        if (loaded != null) {
                            user = loaded;
                            System.out.println("Loaded user: " + user.getName());
                        }
                        break;
                    case "prices": market.displayMarketDetailed(); break;
                    case "quit":
                    case "exit": System.out.println("Goodbye!"); return;
                    default: System.out.println("Unknown command. Type 'help' to see commands.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void help() {
        System.out.println("Commands:");
        System.out.println("  market             - Show current market summary (symbol, price)");
        System.out.println("  prices             - Show detailed market with 24h simulated changes");
        System.out.println("  buy SYMBOL QTY     - Buy quantity of a stock at current market price");
        System.out.println("  sell SYMBOL QTY    - Sell quantity from your holdings");
        System.out.println("  portfolio          - Show your cash, holdings and total value");
        System.out.println("  history            - Show historical portfolio values recorded over time");
        System.out.println("  simulate [N]       - Advance market prices N times (default 1) and record portfolio value");
        System.out.println("  save filename      - Save your portfolio and transactions to files");
        System.out.println("  load filename      - Load your portfolio and transactions from files");
        System.out.println("  help               - Show this help");
        System.out.println("  exit | quit        - Exit simulator");
    }

    private void buy(String symbol, int qty) {
        Stock s = market.getStock(symbol);
        if (s == null) { System.out.println("No such stock: " + symbol); return; }
        if (qty <= 0) { System.out.println("Quantity must be positive"); return; }
        double cost = s.getPrice() * qty;
        if (cost > user.getCashBalance()) { System.out.println("Insufficient funds. Need " + moneyFmt.format(cost)); return; }
        user.debit(cost);
        user.getPortfolio().addHolding(symbol, qty);
        Transaction t = new Transaction(Transaction.Type.BUY, symbol, qty, s.getPrice(), LocalDateTime.now());
        user.addTransaction(t);
        System.out.println("Bought " + qty + " " + symbol + " @ " + moneyFmt.format(s.getPrice()) + " each. Cost: " + moneyFmt.format(cost));
        user.getPortfolio().recordHistory(LocalDateTime.now(), user.getPortfolio().getTotalValue(market));
    }

    private void sell(String symbol, int qty) {
        Stock s = market.getStock(symbol);
        if (s == null) { System.out.println("No such stock: " + symbol); return; }
        if (qty <= 0) { System.out.println("Quantity must be positive"); return; }
        int held = user.getPortfolio().getHoldingQuantity(symbol);
        if (qty > held) { System.out.println("You don't hold that many shares. Held: " + held); return; }
        double proceeds = s.getPrice() * qty;
        user.credit(proceeds);
        user.getPortfolio().removeHolding(symbol, qty);
        Transaction t = new Transaction(Transaction.Type.SELL, symbol, qty, s.getPrice(), LocalDateTime.now());
        user.addTransaction(t);
        System.out.println("Sold " + qty + " " + symbol + " @ " + moneyFmt.format(s.getPrice()) + " each. Proceeds: " + moneyFmt.format(proceeds));
        user.getPortfolio().recordHistory(LocalDateTime.now(), user.getPortfolio().getTotalValue(market));
    }

    private void displayPortfolio() {
        System.out.println("
Portfolio for " + user.getName());
        System.out.println("Cash: " + moneyFmt.format(user.getCashBalance()));
        System.out.println("Holdings:");
        Map<String, Integer> holdings = user.getPortfolio().getHoldings();
        if (holdings.isEmpty()) System.out.println("  (no holdings)");
        else {
            System.out.printf("  %-8s %-8s %-12s %-12s
", "Symbol", "Qty", "Price", "Value");
            for (Map.Entry<String, Integer> e : holdings.entrySet()) {
                Stock s = market.getStock(e.getKey());
                double price = (s==null) ? 0.0 : s.getPrice();
                double val = price * e.getValue();
                System.out.printf("  %-8s %-8d %-12s %-12s
", e.getKey(), e.getValue(), moneyFmt.format(price), moneyFmt.format(val));
            }
        }
        System.out.println("Total portfolio value: " + moneyFmt.format(user.getPortfolio().getTotalValue(market)));
    }

    private void displayHistory() {
        System.out.println("
Portfolio value history:");
        List<Portfolio.HistoryEntry> hist = user.getPortfolio().getHistory();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.printf("  %-20s %-12s
", "Timestamp", "TotalValue");
        for (Portfolio.HistoryEntry h : hist) {
            System.out.printf("  %-20s %-12s
", h.timestamp.format(fmt), moneyFmt.format(h.totalValue));
        }
    }

    private void simulate(int steps) {
        for (int i = 0; i < steps; i++) {
            market.step();
            user.getPortfolio().recordHistory(LocalDateTime.now(), user.getPortfolio().getTotalValue(market));
        }
        System.out.println("Simulated " + steps + " step(s). Market updated.");
        market.displayMarket();
    }

    // ----------------------- OOP classes -----------------------

    static class Stock implements Serializable {
        private final String symbol;
        private final String name;
        private double price; // current price
        private double changePercent; // last change percent

        public Stock(String symbol, String name, double initialPrice) {
            this.symbol = symbol;
            this.name = name;
            this.price = initialPrice;
            this.changePercent = 0.0;
        }

        public String getSymbol() { return symbol; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public double getChangePercent() { return changePercent; }

        public void randomWalkUpdate(Random rnd) {
            // daily/random move +- up to 6%
            double pct = (rnd.nextDouble() * 12.0) - 6.0; // -6% to +6%
            changePercent = pct;
            price = Math.max(0.01, price * (1.0 + pct / 100.0));
        }
    }

    static class Market implements Serializable {
        private final Map<String, Stock> stocks = new HashMap<>();
        private final Random rnd = new Random();

        public void seedSampleStocks() {
            addStock(new Stock("AAPL", "Apple Inc.", 170.00));
            addStock(new Stock("GOOG", "Alphabet Inc.", 130.00));
            addStock(new Stock("MSFT", "Microsoft Corp.", 320.00));
            addStock(new Stock("TSLA", "Tesla Inc.", 260.00));
            addStock(new Stock("AMZN", "Amazon.com Inc.", 150.00));
            addStock(new Stock("INFY", "Infosys Ltd.", 20.00));
            addStock(new Stock("TCS", "Tata Consultancy", 35.00));
        }

        public void addStock(Stock s) { stocks.put(s.getSymbol(), s); }
        public Stock getStock(String symbol) { return stocks.get(symbol); }

        public void step() {
            for (Stock s : stocks.values()) s.randomWalkUpdate(rnd);
        }

        public void displayMarket() {
            System.out.println("
Market snapshot:");
            System.out.printf("  %-8s %-10s %-10s
", "Symbol", "Price", "Change%");
            DecimalFormat fmt = new DecimalFormat("#,##0.00");
            for (Stock s : stocks.values()) {
                System.out.printf("  %-8s %-10s %-10s
", s.getSymbol(), fmt.format(s.getPrice()), String.format("%+.2f", s.getChangePercent()));
            }
        }

        public void displayMarketDetailed() {
            System.out.println("
Market detailed:");
            System.out.printf("  %-8s %-25s %-10s %-10s
", "Symbol", "Name", "Price", "Change%");
            DecimalFormat fmt = new DecimalFormat("#,##0.00");
            for (Stock s : stocks.values()) {
                System.out.printf("  %-8s %-25s %-10s %-10s
", s.getSymbol(), s.getName(), fmt.format(s.getPrice()), String.format("%+.2f", s.getChangePercent()));
            }
        }
    }

    static class User implements Serializable {
        private final String name;
        private double cashBalance;
        private final Portfolio portfolio = new Portfolio();
        private final List<Transaction> transactions = new ArrayList<>();

        public User(String name, double startingCash) {
            this.name = name;
            this.cashBalance = startingCash;
        }

        public String getName() { return name; }
        public double getCashBalance() { return cashBalance; }
        public void debit(double amount) { cashBalance -= amount; }
        public void credit(double amount) { cashBalance += amount; }
        public Portfolio getPortfolio() { return portfolio; }
        public void addTransaction(Transaction t) { transactions.add(t); }
        public List<Transaction> getTransactions() { return transactions; }
    }

    static class Portfolio implements Serializable {
        private final Map<String, Integer> holdings = new HashMap<>();
        private final List<HistoryEntry> history = new ArrayList<>();

        public void addHolding(String symbol, int qty) { holdings.put(symbol, holdings.getOrDefault(symbol, 0) + qty); }
        public void removeHolding(String symbol, int qty) {
            int existing = holdings.getOrDefault(symbol, 0);
            int remaining = existing - qty;
            if (remaining <= 0) holdings.remove(symbol); else holdings.put(symbol, remaining);
        }
        public int getHoldingQuantity(String symbol) { return holdings.getOrDefault(symbol, 0); }
        public Map<String,Integer> getHoldings() { return Collections.unmodifiableMap(holdings); }

        public double getTotalValue(Market market) {
            double total = 0.0;
            for (Map.Entry<String, Integer> e : holdings.entrySet()) {
                Stock s = market.getStock(e.getKey());
                double price = (s == null) ? 0.0 : s.getPrice();
                total += price * e.getValue();
            }
            return total;
        }

        public void recordHistory(LocalDateTime when, double totalValue) {
            history.add(new HistoryEntry(when, totalValue));
        }

        public List<HistoryEntry> getHistory() { return Collections.unmodifiableList(history); }

        static class HistoryEntry implements Serializable {
            public final LocalDateTime timestamp;
            public final double totalValue;
            public HistoryEntry(LocalDateTime ts, double val) { this.timestamp = ts; this.totalValue = val; }
        }
    }

    static class Transaction implements Serializable {
        enum Type { BUY, SELL }
        public final Type type;
        public final String symbol;
        public final int qty;
        public final double price; // price per share at execution
        public final LocalDateTime timestamp;

        public Transaction(Type type, String symbol, int qty, double price, LocalDateTime ts) {
            this.type = type; this.symbol = symbol; this.qty = qty; this.price = price; this.timestamp = ts;
        }
    }

    // ----------------------- Persistence (very simple) -----------------------
    static class Persistence {
        // save to a folder named filename (creates 3 files inside)
        public static void saveUser(User user, String filename) {
            File dir = new File(filename);
            if (!dir.exists()) dir.mkdirs();
            try (PrintWriter pw = new PrintWriter(new File(dir, "profile.txt"))) {
                pw.println("name," + user.getName());
                pw.println("cash," + user.getCashBalance());
            } catch (IOException e) {
                System.out.println("Failed to save profile: " + e.getMessage());
            }
            try (PrintWriter pw = new PrintWriter(new File(dir, "holdings.csv"))) {
                pw.println("symbol,qty");
                for (Map.Entry<String,Integer> e : user.getPortfolio().getHoldings().entrySet()) {
                    pw.println(e.getKey() + "," + e.getValue());
                }
            } catch (IOException e) {
                System.out.println("Failed to save holdings: " + e.getMessage());
            }
            try (PrintWriter pw = new PrintWriter(new File(dir, "transactions.csv"))) {
                pw.println("type,symbol,qty,price,timestamp");
                DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                for (Transaction t : user.getTransactions()) {
                    pw.println(t.type + "," + t.symbol + "," + t.qty + "," + t.price + "," + t.timestamp.format(fmt));
                }
            } catch (IOException e) {
                System.out.println("Failed to save transactions: " + e.getMessage());
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(dir, "portfolioHistory.ser")))) {
                oos.writeObject(user.getPortfolio().getHistory());
            } catch (IOException e) {
                System.out.println("Failed to save history: " + e.getMessage());
            }
            System.out.println("Saved user data to folder: " + dir.getAbsolutePath());
        }

        // load user from folder filename. returns null on failure
        public static User loadUser(String filename) {
            File dir = new File(filename);
            if (!dir.exists() || !dir.isDirectory()) { System.out.println("No such save folder: " + filename); return null; }
            String name = "Trader";
            double cash = 0.0;
            File profile = new File(dir, "profile.txt");
            if (profile.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(profile))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] p = line.split(",",2);
                        if (p.length < 2) continue;
                        if (p[0].equals("name")) name = p[1];
                        if (p[0].equals("cash")) cash = Double.parseDouble(p[1]);
                    }
                } catch (IOException e) { System.out.println("Failed to read profile: " + e.getMessage()); }
            }
            User user = new User(name, cash);
            File holdings = new File(dir, "holdings.csv");
            if (holdings.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(holdings))) {
                    String line = br.readLine(); // header
                    while ((line = br.readLine()) != null) {
                        String[] p = line.split(",");
                        if (p.length < 2) continue;
                        user.getPortfolio().addHolding(p[0].trim(), Integer.parseInt(p[1].trim()));
                    }
                } catch (IOException e) { System.out.println("Failed to read holdings: " + e.getMessage()); }
            }
            File tx = new File(dir, "transactions.csv");
            if (tx.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(tx))) {
                    String line = br.readLine(); // header
                    DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                    while ((line = br.readLine()) != null) {
                        String[] p = line.split(",");
                        if (p.length < 5) continue;
                        Transaction.Type type = Transaction.Type.valueOf(p[0].trim());
                        String symbol = p[1].trim();
                        int qty = Integer.parseInt(p[2].trim());
                        double price = Double.parseDouble(p[3].trim());
                        LocalDateTime ts = LocalDateTime.parse(p[4].trim(), fmt);
                        user.addTransaction(new Transaction(type, symbol, qty, price, ts));
                    }
                } catch (IOException e) { System.out.println("Failed to read transactions: " + e.getMessage()); }
            }
            File hist = new File(dir, "portfolioHistory.ser");
            if (hist.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(hist))) {
                    Object obj = ois.readObject();
                    if (obj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Portfolio.HistoryEntry> list = (List<Portfolio.HistoryEntry>) obj;
                        for (Portfolio.HistoryEntry h : list) user.getPortfolio().history.add(h);
                    }
                } catch (Exception e) { System.out.println("Failed to read history: " + e.getMessage()); }
            }
            return user;
        }
    }
}
