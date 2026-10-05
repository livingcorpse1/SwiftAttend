package edu.swiftattend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSocketTicketService {
    record Ticket(String userId,String role,String sessionId,long expiresAt){}
    private final Map<String,Ticket> tickets=new ConcurrentHashMap<>();private final SecureRandom random=new SecureRandom();private final JdbcTemplate db;
    WebSocketTicketService(JdbcTemplate db){this.db=db;}
    public String issue(String user,String role,String session){UUID id=AttendanceController.uuid(session);int permitted=db.queryForObject("SELECT count(*) FROM attendance_session WHERE id=? AND status='ACTIVE' AND (?='STUDENT' OR teacher_id=CAST(? AS UUID))",Integer.class,id,role,user);if(permitted==0)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"This active session is not available to your account.");byte[] bytes=new byte[32];random.nextBytes(bytes);String ticket=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);tickets.put(ticket,new Ticket(user,role,session,System.currentTimeMillis()+30_000));return ticket;}
    public Ticket consume(String ticket,String session){Ticket t=tickets.remove(ticket);if(t==null||t.expiresAt()<System.currentTimeMillis()||!t.sessionId().equals(session))return null;return t;}
}
