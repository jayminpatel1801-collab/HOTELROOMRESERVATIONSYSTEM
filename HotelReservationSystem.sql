CREATE DATABASE IF NOT EXISTS hotel_db;
USE hotel_db;

CREATE TABLE IF NOT EXISTS rooms (
    room_number INT PRIMARY KEY,
    room_type   VARCHAR(50)   NOT NULL,
    price       DECIMAL(10,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS reservations (
    booking_id    INT AUTO_INCREMENT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    phone         CHAR(10)     NOT NULL,
    room_number   INT          NOT NULL,
    check_in      DATE         NOT NULL,
    check_out     DATE         NOT NULL,
    FOREIGN KEY (room_number) REFERENCES rooms(room_number)
) AUTO_INCREMENT = 1001;

INSERT IGNORE INTO rooms (room_number, room_type, price) VALUES
    (101, 'Standard', 1000),
    (102, 'Standard', 1000),
    (201, 'Deluxe', 1800),
    (202, 'Deluxe', 1800),
    (301, 'Executive', 2800),
    (302, 'Executive', 2800),
    (401, 'Suite', 4500),
    (402, 'Suite', 4500),
    (501, 'Presidential Suite', 8000),
    (502, 'Presidential Suite', 8000);
    

SELECT r.booking_id, r.customer_name, r.phone, r.room_number,
       rm.room_type, r.check_in, r.check_out
FROM reservations r
JOIN rooms rm ON r.room_number = rm.room_number
ORDER BY r.booking_id;


SELECT COUNT(*) AS total_bookings FROM reservations;

-- Total revenue
SELECT SUM(DATEDIFF(r.check_out, r.check_in) * rm.price) AS total_revenue
FROM reservations r
JOIN rooms rm ON r.room_number = rm.room_number;