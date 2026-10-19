import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.Scanner;

public class HotelReservationSystem {

    // ====== DATABASE SETTINGS - change these to match your MySQL ======
    static final String DB_URL = "jdbc:mysql://localhost:3306/hotel_db";
    static final String DB_USER = "root";
    static final String DB_PASSWORD = "jaymin@5757";
    // ==================================================================

    static Scanner input = new Scanner(System.in);
    static Connection conn;

    static final String RESERVATION_SELECT =
            "SELECT r.booking_id, r.customer_name, r.phone, "
                    + "r.check_in, r.check_out, "




                    + "rm.room_number, rm.room_type, rm.price "
                    + "FROM reservations r "
                    + "JOIN rooms rm ON r.room_number = rm.room_number ";

    // Room class
    static class Room {
        int roomNumber;
        String type;
        double price;

        Room(int roomNumber, String type, double price) {
            this.roomNumber = roomNumber;
            this.type = type;
            this.price = price;
        }
    }

    // Reservation class
    static class Reservation {
        int bookingId;
        String name;
        String phone;
        Room room;
        LocalDate checkIn;
        LocalDate checkOut;

        Reservation(int bookingId, String name, String phone,
                    Room room, LocalDate checkIn, LocalDate checkOut) {
            this.bookingId = bookingId;
            this.name = name;
            this.phone = phone;
            this.room = room;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
        }

        long getNights() {
            return ChronoUnit.DAYS.between(checkIn, checkOut);
        }

        double getTotal() {
            return getNights() * room.price;
        }
    }

    public static void main(String[] args) {

        // Connect to MySQL
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Connected to database.");
        } catch (SQLException e) {
            System.out.println("Could not connect to database: " + e.getMessage());
            return;
        }

        int choice;

        do {
            System.out.println("\n====================================");
            System.out.println("   HOTEL ROOM RESERVATION SYSTEM");
            System.out.println("====================================");
            System.out.println("1. Show All Rooms");
            System.out.println("2. Show Available Rooms");
            System.out.println("3. Book a Room");
            System.out.println("4. View All Reservations");
            System.out.println("5. Search Reservation");
            System.out.println("6. Cancel Reservation");
            System.out.println("7. Show Reservation Bill");
            System.out.println("8. Exit");
            System.out.println("====================================");

            choice = readInt("Enter your choice: ");

            try {
                switch (choice) {
                    case 1 -> showAllRooms();
                    case 2 -> showAvailableRooms();
                    case 3 -> bookRoom();
                    case 4 -> viewReservations();
                    case 5 -> searchReservation();
                    case 6 -> cancelReservation();
                    case 7 -> showReservationBill();
                    case 8 -> System.out.println("Thank you for visiting!");
                    default -> System.out.println(
                            "Invalid choice. Please select 1 to 8."
                    );
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
            }

        } while (choice != 8);

        try {
            conn.close();
        } catch (SQLException e) {
            // ignore on exit
        }
        input.close();
    }

    // Safely read an integer
    static int readInt(String message) {
        while (true) {
            try {
                System.out.print(message);
                int number = input.nextInt();
                input.nextLine();
                return number;
            } catch (InputMismatchException e) {
                System.out.println("Error: Please enter a valid number.");
                input.nextLine();
            }
        }
    }

    // Safely read a date
    static LocalDate readDate(String message) {
        while (true) {
            try {
                System.out.print(message);
                return LocalDate.parse(input.nextLine().trim());
            } catch (DateTimeParseException e) {
                System.out.println(
                        "Invalid date! Use YYYY-MM-DD, e.g. 2026-11-15."
                );
            }
        }
    }

    // Validate check-in and check-out dates
    static boolean validDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn.isBefore(LocalDate.now())) {
            System.out.println("Check-in date cannot be in the past.");
            return false;
        }

        if (!checkOut.isAfter(checkIn)) {
            System.out.println("Check-out must be after check-in.");
            return false;
        }

        return true;
    }

    // ---------- Database helper methods ----------

    // Load all rooms from the database
    static ArrayList<Room> getAllRooms() throws SQLException {
        ArrayList<Room> list = new ArrayList<>();
        String sql = "SELECT room_number, room_type, price FROM rooms "
                + "ORDER BY room_number";

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Room(
                        rs.getInt("room_number"),
                        rs.getString("room_type"),
                        rs.getDouble("price")
                ));
            }
        }

        return list;
    }

    // Find one room by number (null if not found)
    static Room findRoom(int roomNumber) throws SQLException {
        String sql = "SELECT room_number, room_type, price FROM rooms "
                + "WHERE room_number = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Room(
                            rs.getInt("room_number"),
                            rs.getString("room_type"),
                            rs.getDouble("price")
                    );
                }
            }
        }

        return null;
    }

    // Turn the current row of a joined query into a Reservation
    static Reservation mapReservation(ResultSet rs) throws SQLException {
        Room room = new Room(
                rs.getInt("room_number"),
                rs.getString("room_type"),
                rs.getDouble("price")
        );

        return new Reservation(
                rs.getInt("booking_id"),
                rs.getString("customer_name"),
                rs.getString("phone"),
                room,
                rs.getDate("check_in").toLocalDate(),
                rs.getDate("check_out").toLocalDate()
        );
    }

    // Check whether a room is available for selected dates
    static boolean isAvailable(Room room,
                               LocalDate checkIn,
                               LocalDate checkOut) throws SQLException {

        // Overlap: existing.check_in < newCheckOut AND existing.check_out > newCheckIn
        String sql = "SELECT COUNT(*) FROM reservations "
                + "WHERE room_number = ? AND check_in < ? AND check_out > ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, room.roomNumber);
            ps.setDate(2, Date.valueOf(checkOut));
            ps.setDate(3, Date.valueOf(checkIn));

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 0;
            }
        }
    }

    // Find a reservation using its booking ID
    static Reservation findReservation(int id) throws SQLException {
        String sql = RESERVATION_SELECT + "WHERE r.booking_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapReservation(rs);
                }
            }
        }

        return null;
    }

    // ---------- Menu options ----------

    // Option 1: Show all rooms
    static void showAllRooms() throws SQLException {
        System.out.println("\n========== ALL HOTEL ROOMS ==========");

        for (Room room : getAllRooms()) {
            System.out.println(
                    "Room " + room.roomNumber
                            + " | Type: " + room.type
                            + " | Price: Rs. " + room.price + "/night"
            );
        }
    }

    // Option 2: Show available rooms for selected dates
    static void showAvailableRooms() throws SQLException {
        System.out.println("\n====== CHECK ROOM AVAILABILITY ======");

        LocalDate checkIn =
                readDate("Enter check-in date (YYYY-MM-DD): ");

        LocalDate checkOut =
                readDate("Enter check-out date (YYYY-MM-DD): ");

        if (!validDates(checkIn, checkOut)) {
            return;
        }

        boolean found = false;

        System.out.println("\nAvailable rooms from "
                + checkIn + " to " + checkOut + ":");

        for (Room room : getAllRooms()) {
            if (isAvailable(room, checkIn, checkOut)) {
                System.out.println(
                        "Room " + room.roomNumber
                                + " | " + room.type
                                + " | Rs. " + room.price + "/night"
                );
                found = true;
            }
        }

        if (!found) {
            System.out.println("No rooms available for these dates.");
        }
    }

    // Option 3: Book a room
    static void bookRoom() throws SQLException {
        System.out.println("\n========== NEW ROOM BOOKING ==========");

        System.out.print("Enter customer name: ");
        String name = input.nextLine().trim();

        if (name.isEmpty()) {
            System.out.println("Customer name cannot be empty.");
            return;
        }

        System.out.print("Enter 10-digit phone number: ");
        String phone = input.nextLine().trim();

        if (!phone.matches("[0-9]{10}")) {
            System.out.println("Invalid phone number.");
            return;
        }

        LocalDate checkIn =
                readDate("Enter check-in date (YYYY-MM-DD): ");

        LocalDate checkOut =
                readDate("Enter check-out date (YYYY-MM-DD): ");

        if (!validDates(checkIn, checkOut)) {
            return;
        }

        System.out.println("\nSelect Room Type:");
        System.out.println("1. Standard - Rs. 1000/night");
        System.out.println("2. Deluxe - Rs. 1800/night");
        System.out.println("3. Executive - Rs. 2800/night");
        System.out.println("4. Suite - Rs. 4500/night");
        System.out.println("5. Presidential Suite - Rs. 8000/night");

        int typeChoice = readInt("Enter room type choice: ");

        String selectedType;

        switch (typeChoice) {
            case 1 -> selectedType = "Standard";
            case 2 -> selectedType = "Deluxe";
            case 3 -> selectedType = "Executive";
            case 4 -> selectedType = "Suite";
            case 5 -> selectedType = "Presidential Suite";
            default -> {
                System.out.println("Invalid room type.");
                return;
            }
        }

        System.out.println("\nAvailable rooms of type " + selectedType + ":");

        boolean found = false;

        for (Room room : getAllRooms()) {
            if (room.type.equals(selectedType)
                    && isAvailable(room, checkIn, checkOut)) {
                System.out.println(
                        "Room " + room.roomNumber
                                + " | Rs. " + room.price + "/night"
                );
                found = true;
            }
        }

        if (!found) {
            System.out.println("No rooms of this type are available.");
            return;
        }

        int roomNumber = readInt("Enter room number to book: ");

        Room selectedRoom = findRoom(roomNumber);

        if (selectedRoom == null
                || !selectedRoom.type.equals(selectedType)
                || !isAvailable(selectedRoom, checkIn, checkOut)) {
            System.out.println(
                    "Invalid room or room unavailable for those dates."
            );
            return;
        }

        // Save the booking in MySQL (booking_id is auto-generated)
        String sql = "INSERT INTO reservations "
                + "(customer_name, phone, room_number, check_in, check_out) "
                + "VALUES (?, ?, ?, ?, ?)";

        int bookingId;

        try (PreparedStatement ps = conn.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setInt(3, selectedRoom.roomNumber);
            ps.setDate(4, Date.valueOf(checkIn));
            ps.setDate(5, Date.valueOf(checkOut));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                bookingId = keys.getInt(1);
            }
        }

        System.out.println("\nBooking successful!");
        System.out.println("Your Booking ID is: " + bookingId);

        showReservationBillDetails(findReservation(bookingId));
    }

    // Option 4: View all reservations
    static void viewReservations() throws SQLException {
        System.out.println("\n========== ALL RESERVATIONS ==========");

        boolean any = false;

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     RESERVATION_SELECT + "ORDER BY r.booking_id")) {

            while (rs.next()) {
                Reservation r = mapReservation(rs);
                any = true;

                System.out.println("--------------------------------------");
                System.out.println("Booking ID : " + r.bookingId);
                System.out.println("Customer   : " + r.name);
                System.out.println("Phone      : " + r.phone);
                System.out.println("Room       : " + r.room.roomNumber);
                System.out.println("Room Type  : " + r.room.type);
                System.out.println("Check-in   : " + r.checkIn);
                System.out.println("Check-out  : " + r.checkOut);
                System.out.println("Nights     : " + r.getNights());
                System.out.println("Total      : Rs. " + r.getTotal());
            }
        }

        if (!any) {
            System.out.println("No reservations found.");
        }
    }

    // Option 5: Search reservation
    static void searchReservation() throws SQLException {
        System.out.println("\n========== SEARCH RESERVATION ==========");

        int id = readInt("Enter booking ID to search: ");
        Reservation r = findReservation(id);

        if (r == null) {
            System.out.println("Reservation not found.");
        } else {
            System.out.println("Reservation found!");
            showReservationBillDetails(r);
        }
    }

    // Option 6: Cancel reservation
    static void cancelReservation() throws SQLException {
        System.out.println("\n========== CANCEL RESERVATION ==========");

        int id = readInt("Enter booking ID to cancel: ");
        Reservation r = findReservation(id);

        if (r == null) {
            System.out.println("Reservation not found.");
            return;
        }

        System.out.println("Booking ID : " + r.bookingId);
        System.out.println("Customer   : " + r.name);
        System.out.println("Room       : " + r.room.roomNumber);
        System.out.println("Check-in   : " + r.checkIn);
        System.out.println("Check-out  : " + r.checkOut);

        System.out.print("Confirm cancellation? (yes/no): ");
        String confirm = input.nextLine().trim();

        if (confirm.equalsIgnoreCase("yes")) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM reservations WHERE booking_id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            System.out.println("Reservation cancelled successfully.");
            System.out.println("The room is available for those dates.");
        } else {
            System.out.println("Cancellation stopped. Booking retained.");
        }
    }

    // Option 7: Show reservation bill
    static void showReservationBill() throws SQLException {
        System.out.println("\n========== RESERVATION BILL ==========");

        int id = readInt("Enter booking ID for the bill: ");
        Reservation r = findReservation(id);

        if (r == null) {
            System.out.println("Reservation not found.");
            return;
        }

        showReservationBillDetails(r);
    }

    // Display bill details
    static void showReservationBillDetails(Reservation r) {
        System.out.println("\n======================================");
        System.out.println("              HOTEL BILL");
        System.out.println("======================================");
        System.out.println("Booking ID   : " + r.bookingId);
        System.out.println("Customer     : " + r.name);
        System.out.println("Phone Number : " + r.phone);
        System.out.println("Room Number  : " + r.room.roomNumber);
        System.out.println("Room Type    : " + r.room.type);
        System.out.println("Check-in     : " + r.checkIn);
        System.out.println("Check-out    : " + r.checkOut);
        System.out.println("Total Nights : " + r.getNights());
        System.out.println("--------------------------------------");
        System.out.println("Price/Night  : Rs. " + r.room.price);
        System.out.println("Total Amount : Rs. " + r.getTotal());
        System.out.println("======================================");
        System.out.println("       Thank you for visiting!");
        System.out.println("======================================");
    }
}