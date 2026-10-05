package edu.swiftattend;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSocket
class SocketConfig implements WebSocketConfigurer {
    private final SessionSocketHandler handler; private final JdbcTemplate db; private final WebSocketTicketService tickets; private final String origin;
    SocketConfig(SessionSocketHandler h,JdbcTemplate d,WebSocketTicketService tickets,@Value("${app.cors.origin}") String o){handler=h;db=d;this.tickets=tickets;origin=o;}
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry r){r.addHandler(handler,"/ws/attendance/{sessionId}").addInterceptors(new HandshakeInterceptor(){
        @Override public boolean beforeHandshake(ServerHttpRequest req,ServerHttpResponse res,WebSocketHandler h,Map<String,Object> attrs){
            try{String query=req.getURI().getQuery();String ticket=Arrays.stream(query==null?new String[0]:query.split("&")).filter(x->x.startsWith("ticket=")).map(x->java.net.URLDecoder.decode(x.substring(7),StandardCharsets.UTF_8)).findFirst().orElse("");String path=req.getURI().getPath();String sessionId=path.substring(path.lastIndexOf('/')+1);var identity=tickets.consume(ticket,sessionId);if(identity==null)return false;UUID id=UUID.fromString(sessionId);Integer active=db.queryForObject("SELECT count(*) FROM attendance_session WHERE id=? AND status='ACTIVE'",Integer.class,id);if(active==null||active==0)return false;attrs.put("userId",identity.userId());attrs.put("role",identity.role());attrs.put("sessionId",sessionId);return true;}catch(Exception ignored){return false;}}
        @Override public void afterHandshake(ServerHttpRequest req,ServerHttpResponse res,WebSocketHandler h,Exception e){}
    }).setAllowedOriginPatterns(origin);}
}

@Component
public class SessionSocketHandler extends TextWebSocketHandler {
    private static final Map<UUID,Set<WebSocketSession>> ROOMS=new ConcurrentHashMap<>();
    private final JdbcTemplate db; private final AttendanceController attendance; private final ObjectMapper json=new ObjectMapper();
    SessionSocketHandler(JdbcTemplate db,AttendanceController a){this.db=db;attendance=a;}
    @Override public void afterConnectionEstablished(WebSocketSession socket)throws Exception{
        UUID id=UUID.fromString((String)socket.getAttributes().get("sessionId"));
        ROOMS.computeIfAbsent(id,k->ConcurrentHashMap.newKeySet()).add(socket); Map<String,Object> payload=attendance.publicQr(id);if(payload!=null) synchronized(socket){socket.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","qr_heartbeat","payload",payload,"serverTime",System.currentTimeMillis()))));}
    }
    @Override public void afterConnectionClosed(WebSocketSession socket,CloseStatus status){for(Set<WebSocketSession> room:ROOMS.values())room.remove(socket);ROOMS.entrySet().removeIf(e->e.getValue().isEmpty());}
    static void broadcast(UUID id,Map<String,Object> message){Set<WebSocketSession> room=ROOMS.get(id);if(room==null)return;String text;try{text=new ObjectMapper().writeValueAsString(message);}catch(Exception e){return;}for(WebSocketSession s:room)if(s.isOpen())try{synchronized(s){s.sendMessage(new TextMessage(text));}}catch(Exception ignored){}}
    @Scheduled(fixedRate=1000) public void pulse(){for(var row:db.queryForList("SELECT id FROM attendance_session WHERE status='ACTIVE'")){UUID id=(UUID)row.get("id");Map<String,Object> payload=attendance.publicQr(id);if(payload!=null)broadcast(id,Map.of("type","qr_heartbeat","payload",payload,"serverTime",System.currentTimeMillis()));}}
    @Scheduled(fixedRate=60000) public void clearOldNonceRows(){db.update("DELETE FROM used_qr_nonce WHERE used_at < now() - interval '10 minutes'");}
}

