<?php
include('db_login_data.php');

$conn = new mysqli($dbServername, $dbUsername, $dbPassword, $dbDatabase);

// check if the connection works
if ($conn->connect_error) {
    die('{"status": "error", "message": "Database connection failed"}');
}
