# PulseGrid IoT Management System

PulseGrid is a database-backed IoT inventory and historical telemetry web application. It lets each registered account manage its own devices, sensors, and manually recorded sensor readings.

The application uses Java 8, JSP/Servlets, Maven, Tomcat 9, JDBC, and Oracle XE. It is a management interface for stored records; it does not connect directly to physical IoT hardware or ingest live telemetry.

## What users can do

- Create an account and securely sign in with a username or email address.
- Add, edit, and delete their own devices.
- Add, edit, and delete sensors attached to their devices.
- Add, edit, filter, and delete historical sensor readings.
- View a dashboard showing only their own device, sensor, reading, and device-status totals.
- Log out securely.

Each account is isolated from other accounts at the server level. A user cannot view or change another user’s devices, sensors, or readings by changing a page URL or form value.

Deleting a device removes that device’s readings first, then its sensors, then the device in one database transaction. If any deletion fails, the transaction is rolled back.

## Requirements

- JDK 8
- Apache Maven 3.x
- Apache Tomcat 9.x
- Oracle XE 11g or compatible Oracle instance
- Oracle database account with access to the application tables

The bundled JDBC dependency targets Oracle’s `ojdbc6` driver. The standard local database URL is configured in `src/main/java/com/iot/config/DBConnection.java`:

```text
jdbc:oracle:thin:@//localhost:1521/xe
```

For another database host, service name, or port, update that URL before building.

## Database expectations

PulseGrid uses the existing schema and does not create, drop, or migrate tables. The database account must be able to read and write these tables:

| Table | Required relationship / fields used by the application |
| --- | --- |
| `USERS` | `USER_ID`, `USER_NAME`, `PASSWORD_HASH`, `EMAIL`, `LOCATION` |
| `DEVICES` | `DEVICE_ID`, `DEVICE_NAME`, `USER_ID`, `DEVICE_TYPE`, `STATUS` |
| `SENSORS` | `SENSOR_ID`, `SENSOR_NAME`, `DEVICE_ID`, `SENSOR_TYPE`, `UNIT` |
| `SENSOR_READINGS` | `READING_ID`, `SENSOR_ID`, `READING_VALUE`, `READING_TIME` |

Relationships are:

```text
USERS → DEVICES → SENSORS → SENSOR_READINGS
```

New passwords are stored as BCrypt hashes in `USERS.PASSWORD_HASH`; plaintext passwords are never stored. Existing accounts can sign in only if their `PASSWORD_HASH` value is a valid BCrypt hash. Use registration to create a new account when needed.

## Configure database credentials

The application reads its Oracle credentials from environment variables instead of source code:

```powershell
$env:IOT_DB_USER = 'your_oracle_user'
$env:IOT_DB_PASSWORD = 'your_oracle_password'
```

Set these in the same PowerShell session that starts Tomcat. For a permanent installation, define them as system or service environment variables and restart the Tomcat service/process afterward.

Do not commit credentials to the project or add them to `DBConnection.java`.

## Build

From the project directory:

```powershell
cd C:\Users\ASUS\Downloads\iot-management
mvn clean package
```

The deployable application is created at:

```text
target\iot-management.war
```

## Deploy to Tomcat on Windows

Update `$tomcatDir` if Tomcat is installed elsewhere. Run this from the project folder after a successful build:

```powershell
$tomcatDir = 'C:\Users\ASUS\Downloads\apache-tomcat-9.0.122-windows-x64\apache-tomcat-9.0.122'
$sourceWar = 'C:\Users\ASUS\Downloads\iot-management\target\iot-management.war'

$env:IOT_DB_USER = 'your_oracle_user'
$env:IOT_DB_PASSWORD = 'your_oracle_password'

& "$tomcatDir\bin\shutdown.bat"
Start-Sleep -Seconds 5

Copy-Item -LiteralPath $sourceWar -Destination "$tomcatDir\webapps\iot-management.war" -Force

# Clears only generated JSP classes for this web application. It does not touch Oracle data.
Remove-Item -LiteralPath "$tomcatDir\work\Catalina\localhost\iot-management" -Recurse -Force -ErrorAction SilentlyContinue

& "$tomcatDir\bin\startup.bat"
```

Open the application at:

```text
http://localhost:8081/iot-management/login
```

If your Tomcat connector uses another port, replace `8081` with that port.

## First use

1. Open the login page.
2. Select **Create an account**.
3. Enter a username, email address, optional location, and a password of 8–72 characters.
4. Sign in with the new username or email address and password.
5. Go to **Devices** and add a device. It is automatically assigned to the signed-in account.
6. Go to **Sensors** and add a sensor to one of your devices.
7. Go to **Readings** and add manual historical values for one of your sensors.

The dashboard updates its statistics from records owned by the currently signed-in account.

## Navigation

- **Dashboard** — current user’s inventory statistics and device status overview.
- **Devices** — current user’s devices.
- **Sensors** — sensors attached to the current user’s devices.
- **Readings** — stored readings from the current user’s sensors.
- **Log out** — ends the authenticated session.

The legacy Users management backend remains in the project, but it is intentionally not shown in the main navigation.

## Troubleshooting

### “Set IOT_DB_USER and IOT_DB_PASSWORD first.”

Tomcat was started without the required environment variables. Stop Tomcat, set both variables in the same PowerShell session, then start it again.

### A page displays old controls or a JSP compilation error after deployment

Stop Tomcat and clear only this application’s generated JSP cache before starting it again:

```powershell
Remove-Item -LiteralPath "$tomcatDir\work\Catalina\localhost\iot-management" -Recurse -Force
```

### The application cannot connect to Oracle

Check that Oracle XE is running, the configured database URL matches the listener/service, and the database account has access to all four tables. Check Tomcat logs in `logs\` for the full server-side error; do not expose database credentials in screenshots or logs.

### A user sees no records

This is expected for a newly registered account. Records are intentionally scoped to the account that owns the device. Add a device, then its sensors and readings.

## Security notes

- Passwords use BCrypt hashes.
- Login, registration, logout, and management forms use server-side validation.
- State-changing forms require CSRF tokens.
- Authenticated routes require a valid session.
- Ownership is verified on the server for devices, sensors, and readings.

For production hosting, use HTTPS, a reverse proxy or properly configured TLS connector, restricted Oracle credentials, regular database backups, and a Tomcat service account with only the filesystem permissions it needs.
