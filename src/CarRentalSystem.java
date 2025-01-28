import java.sql.*;
import java.util.Scanner;

public class CarRentalSystem {

    // Конфигурация для подключения к базе данных PostgreSQL
    private static final String URL = "jdbc:postgresql://localhost:5432/CarRentalDB"; // Замените на ваш URL
    private static final String USER = "postgres";  // Замените на ваше имя пользователя
    private static final String PASSWORD = "E00244631";  // Замените на ваш пароль

    // Получение подключения к базе данных
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    // Метод для отображения доступных автомобилей
    public static void showAvailableCars() throws SQLException {
        String query = "SELECT * FROM Cars WHERE availability = TRUE"; // Выбираем только доступные автомобили
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            System.out.println("Доступные автомобили для аренды:");
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("id") + ", Название: " + rs.getString("name") + ", Модель: " + rs.getString("model") +
                        ", Цена в день: " + rs.getDouble("price_per_day") + " KZT");
            }
        }
    }

    // Метод для проверки, существует ли пользователь с заданным ID
    public static boolean userExists(int userId) throws SQLException {
        String query = "SELECT id FROM Users WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next(); // Если пользователь найден, возвращаем true
        }
    }

    // Метод для добавления нового пользователя в базу данных
    public static void addNewUser(int userId) throws SQLException {
        Scanner scanner = new Scanner(System.in);

        // Запрос данных для нового пользователя
        System.out.print("Введите имя нового пользователя: ");
        String name = scanner.nextLine();
        System.out.print("Введите email нового пользователя: ");
        String email = scanner.nextLine();
        System.out.print("Введите телефон нового пользователя: ");
        String phone = scanner.nextLine();

        // Вставка нового пользователя в таблицу Users
        String insertUserQuery = "INSERT INTO Users (id, name, email, phone) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(insertUserQuery)) {
            stmt.setInt(1, userId);
            stmt.setString(2, name);
            stmt.setString(3, email);
            stmt.setString(4, phone);
            stmt.executeUpdate();
            System.out.println("Новый пользователь с ID " + userId + " был успешно добавлен в базу данных.");
        }
    }

    // Метод для проверки, занята ли машина в указанном диапазоне дат
    public static boolean isCarOccupied(int carId, Date startDate, Date endDate) throws SQLException {
        String query =
                "SELECT COUNT(*) FROM RentalDays " +
                        "WHERE rental_id IN (SELECT id FROM Rentals WHERE car_id = ?) " +
                        "AND rental_date BETWEEN ? AND ?";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, carId);           // Указываем ID автомобиля
            stmt.setDate(2, startDate);      // Указываем дату начала аренды
            stmt.setDate(3, endDate);        // Указываем дату окончания аренды

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;  // Если есть записи, значит машина занята
            }
            return false; // Если машинa не занята
        }
    }

    // Метод для добавления дней аренды в таблицу RentalDays
    public static void addRentalDays(int rentalId, Date startDate, Date endDate) throws SQLException {
        String insertRentalDaysQuery = "INSERT INTO RentalDays (rental_id, rental_date) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(insertRentalDaysQuery)) {
            long diffInMillies = endDate.getTime() - startDate.getTime();
            long diffDays = diffInMillies / (1000 * 60 * 60 * 24); // Количество дней аренды

            for (int i = 0; i <= diffDays; i++) {
                // Генерация даты аренды
                Date rentalDay = new Date(startDate.getTime() + (i * (1000 * 60 * 60 * 24)));
                stmt.setInt(1, rentalId);
                stmt.setDate(2, rentalDay);
                stmt.executeUpdate();
            }
            System.out.println("Дни аренды успешно добавлены.");
        }
    }

    // Метод для аренды автомобиля
    public static void rentCar(int carId, int userId, Date startDate, Date endDate) throws SQLException {
        // Проверка существования пользователя
        if (userId == 0 || !userExists(userId)) {
            System.out.println("Пользователь с ID " + userId + " не найден.");
            System.out.print("Хотите создать нового пользователя? (yes/no): ");
            Scanner scanner = new Scanner(System.in);
            String response = scanner.nextLine().toLowerCase();
            if (response.equals("yes")) {
                if (userId == 0) {
                    System.out.print("Введите ID нового пользователя: ");
                    userId = scanner.nextInt();
                }
                addNewUser(userId); // Добавляем нового пользователя в базу данных
            } else {
                System.out.println("Операция аренды отменена.");
                return;
            }
        }

        // Проверка занятости машины в выбранный день
        if (isCarOccupied(carId, startDate, endDate)) {
            System.out.println("Машина уже занята в выбранные даты. Пожалуйста, выберите другой день.");
            return;
        }

        // Получаем цену автомобиля
        String priceQuery = "SELECT price_per_day FROM Cars WHERE id = ?";
        double pricePerDay = 0;
        try (Connection conn = getConnection(); PreparedStatement priceStmt = conn.prepareStatement(priceQuery)) {
            priceStmt.setInt(1, carId);
            ResultSet priceRs = priceStmt.executeQuery();
            if (priceRs.next()) {
                pricePerDay = priceRs.getDouble("price_per_day");
            }

            // Рассчитываем стоимость аренды
            long diffInMillies = endDate.getTime() - startDate.getTime();
            long diffDays = diffInMillies / (1000 * 60 * 60 * 24); // Количество дней аренды
            double totalPrice = pricePerDay * diffDays;

            // Вставляем запись о аренде
            String rentQuery = "INSERT INTO Rentals (car_id, user_id, start_date, end_date, total_price) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement rentStmt = getConnection().prepareStatement(rentQuery, Statement.RETURN_GENERATED_KEYS)) {
                rentStmt.setInt(1, carId);
                rentStmt.setInt(2, userId);
                rentStmt.setDate(3, startDate);
                rentStmt.setDate(4, endDate);
                rentStmt.setDouble(5, totalPrice);
                rentStmt.executeUpdate();

                // Получаем ID только что вставленной аренды
                ResultSet generatedKeys = rentStmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int rentalId = generatedKeys.getInt(1);
                    addRentalDays(rentalId, startDate, endDate);  // Добавляем дни аренды
                }

                System.out.println("Аренда успешно оформлена! Общая стоимость аренды: " + totalPrice + " KZT");
            }
        }
    }

    // Основной метод
    public static void main(String[] args) throws SQLException {
        Scanner scanner = new Scanner(System.in);

        // Показываем доступные автомобили
        showAvailableCars();

        // Вводим данные
        System.out.print("Введите ID автомобиля для аренды: ");
        int carId = scanner.nextInt();
        System.out.print("Введите ID пользователя (или 0 для создания нового пользователя): ");
        int userId = scanner.nextInt();
        System.out.print("Введите дату начала аренды (YYYY-MM-DD): ");
        String startDateStr = scanner.next();
        System.out.print("Введите дату окончания аренды (YYYY-MM-DD): ");
        String endDateStr = scanner.next();

        Date startDate = Date.valueOf(startDateStr);
        Date endDate = Date.valueOf(endDateStr);

        // Оформляем аренду
        rentCar(carId, userId, startDate, endDate);
    }
}
