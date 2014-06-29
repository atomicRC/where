<%@ page import="org.example.where.gae.WhereService" %>
<%@ page import="java.util.Date" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>Where status</title>
</head>
<body>
<%= new Date() %><br/>
<strong>Where Service status</strong><br/>
Pending Location Requests: <%= WhereService.getLocationRequestsCount() %>

</body>