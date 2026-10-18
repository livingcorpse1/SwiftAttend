package edu.swiftattend;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.SecretKey;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.HexFormat;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate db; private final PasswordEncoder passwords; private final SecurityConfig security; private final SecretKey jwtKey; private final WebSocketTicketService wsTickets;
    private final SecureRandom random=new SecureRandom();
    @org.springframework.beans.factory.annotation.Value("${app.cookie.secure}") boolean secureCookie;
    AuthController(JdbcTemplate db,PasswordEncoder p,SecurityConfig s,SecretKey k,WebSocketTicketService tickets){this.db=db;passwords=p;security=s;jwtKey=k;wsTickets=tickets;}
    record Register(@NotBlank @Size(min=2,max=100) String name,@Email @NotBlank String email,@Size(min=10,max=100) String password,@Pattern(regexp="STUDENT|TEACHER") String role,String enrollmentNumber,String department,@Size(max=60) String batch){}
    record Login(@Email @NotBlank String email,@NotBlank String password){}
    record Profile(@NotBlank @Size(min=2,max=100) String name,@NotBlank @Size(max=120) String department,@Size(max=60) String batch){}
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> register(@Valid @RequestBody Register r){
        String role=r.role()==null?"STUDENT":r.role(); String email=r.email().trim().toLowerCase();
        String enrollment=r.enrollmentNumber()==null||r.enrollmentNumber().isBlank()?null:r.enrollmentNumber().trim();
        if("STUDENT".equals(role)&&(r.enrollmentNumber()==null||r.enrollmentNumber().isBlank())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enrollment number is required for students.");
        if("STUDENT".equals(role)&&(r.batch()==null||r.batch().isBlank())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Class or batch is required for students.");
        if (exists("SELECT EXISTS(SELECT 1 FROM app_user WHERE email=?)", email))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This email already has an account. Sign in, or use a different email.");
        if (enrollment != null && exists("SELECT EXISTS(SELECT 1 FROM app_user WHERE enrollment_number=?)", enrollment))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This enrollment number is already linked to an account. Sign in with that account or check the number.");
        UUID id=UUID.randomUUID(); try { db.update("INSERT INTO app_user(id,name,email,password_hash,role,enrollment_number,department,batch) VALUES(?,?,?,?,?,?,?,?)",id,r.name().trim(),email,passwords.encode(r.password()),role,enrollment,r.department()==null?"Computer Science & Engineering":r.department().trim(),"STUDENT".equals(role)?r.batch().trim():null); }
        catch(org.springframework.dao.DuplicateKeyException e){
            if (exists("SELECT EXISTS(SELECT 1 FROM app_user WHERE email=?)", email))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"This email already has an account. Sign in, or use a different email.");
            if (enrollment != null && exists("SELECT EXISTS(SELECT 1 FROM app_user WHERE enrollment_number=?)", enrollment))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"This enrollment number is already linked to an account. Sign in with that account or check the number.");
            throw new ResponseStatusException(HttpStatus.CONFLICT,"These account details conflict with an existing record. Check the email and enrollment number.");
        }
        return response(id,r.name().trim(),email,role,r.enrollmentNumber(),r.department()==null?"Computer Science & Engineering":r.department().trim(),r.batch());
    }
    private boolean exists(String sql,Object value){return Boolean.TRUE.equals(db.queryForObject(sql,Boolean.class,value));}
    @org.springframework.beans.factory.annotation.Value("${app.refresh.days}") long refreshDays;
    @PostMapping("/login") Map<String,Object> login(@Valid @RequestBody Login l,jakarta.servlet.http.HttpServletResponse response){
        var users=db.query("SELECT id,name,email,password_hash,role,enrollment_number,department,batch,face_descriptor IS NOT NULL AS face_enrolled FROM app_user WHERE email=?",(rs,n)->new Object[]{rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("email"),rs.getString("password_hash"),rs.getString("role"),rs.getString("enrollment_number"),rs.getString("department"),rs.getString("batch"),rs.getBoolean("face_enrolled")},l.email().trim().toLowerCase());
        if(users.isEmpty()||!passwords.matches(l.password(),(String)users.get(0)[3])) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
        Object[] u=users.get(0); issueRefresh((UUID)u[0],response);var result=new java.util.HashMap<>(response((UUID)u[0],(String)u[1],(String)u[2],(String)u[4],(String)u[5],(String)u[6],(String)u[7])); var user=new java.util.HashMap<>((Map<String,Object>)result.get("user"));user.put("faceEnrolled",u[8]);result.put("user",user);return result;
    }
    private Map<String,Object> response(UUID id,String name,String email,String role,String enrollment,String department,String batch){var user=new java.util.HashMap<String,Object>();user.put("id",id);user.put("name",name);user.put("email",email);user.put("role",role);user.put("enrollmentNumber",enrollment==null?"":enrollment);user.put("department",department);user.put("batch",batch==null?"":batch);user.put("faceEnrolled",false);return Map.of("success",true,"token",security.issueToken(id.toString(),role,jwtKey),"user",user);}
    @PostMapping("/refresh") Map<String,Object> refresh(@CookieValue(value="refreshToken",required=false) String token,jakarta.servlet.http.HttpServletResponse response){if(token==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session expired. Please sign in again.");var rows=db.query("SELECT id,name,email,role,enrollment_number,department,batch,face_descriptor IS NOT NULL AS face_enrolled FROM app_user WHERE refresh_token_hash=?",(rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getBoolean(8)},hash(token));if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session expired. Please sign in again.");Object[] u=rows.get(0);issueRefresh((UUID)u[0],response);Map<String,Object> result=new java.util.HashMap<>(response((UUID)u[0],(String)u[1],(String)u[2],(String)u[3],(String)u[4],(String)u[5],(String)u[6]));Map<String,Object> user=new java.util.HashMap<>((Map<String,Object>)result.get("user"));user.put("faceEnrolled",u[7]);result.put("user",user);return result;}
    @GetMapping("/me") Map<String,Object> me(@org.springframework.security.core.annotation.AuthenticationPrincipal String id){return db.queryForMap("SELECT id,name,email,role,enrollment_number AS \"enrollmentNumber\",department,batch FROM app_user WHERE id=?",UUID.fromString(id));}
    @PutMapping("/me") Map<String,Object> updateProfile(@org.springframework.security.core.annotation.AuthenticationPrincipal String id,@Valid @RequestBody Profile p){UUID userId=UUID.fromString(id);String batch=p.batch()==null?null:p.batch().trim();db.update("UPDATE app_user SET name=?,department=?,batch=CASE WHEN role='STUDENT' AND CAST(? AS VARCHAR) IS NOT NULL THEN CAST(? AS VARCHAR) ELSE batch END WHERE id=?",p.name().trim(),p.department().trim(),batch,batch,userId);return Map.of("success",true,"name",p.name().trim(),"department",p.department().trim(),"batch",batch==null?"":batch);}
    @PostMapping("/ws-ticket") Map<String,Object> wsTicket(@org.springframework.security.core.annotation.AuthenticationPrincipal String id,org.springframework.security.core.Authentication auth,@RequestBody Map<String,String> body){String role=auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_","");return Map.of("ticket",wsTickets.issue(id,role,body.get("sessionId")),"expiresInSeconds",30);}
    @PostMapping("/logout") Map<String,Object> logout(@org.springframework.security.core.annotation.AuthenticationPrincipal String id,jakarta.servlet.http.HttpServletResponse response){db.update("UPDATE app_user SET refresh_token_hash=NULL WHERE id=?",UUID.fromString(id));response.addHeader("Set-Cookie",cookie("",0).toString());return Map.of("success",true,"message","Signed out.");}
    @PostMapping("/face-descriptor") Map<String,Object> enroll(@org.springframework.security.core.annotation.AuthenticationPrincipal String id,@RequestBody Map<String,Object> body){
        var arr=body.get("faceDescriptor"); if(!(arr instanceof java.util.List<?> list)||list.size()!=128||list.stream().anyMatch(x->!(x instanceof Number n)||!Double.isFinite(n.doubleValue()))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A valid 128-value face descriptor is required.");
        try { db.update("UPDATE app_user SET face_descriptor=? WHERE id=?",new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(list),UUID.fromString(id)); } catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unable to save the face template.");}
        return Map.of("success",true,"message","Face template enrolled.");
    }
    private void issueRefresh(UUID user,jakarta.servlet.http.HttpServletResponse response){byte[] b=new byte[48];random.nextBytes(b);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(b);db.update("UPDATE app_user SET refresh_token_hash=? WHERE id=?",hash(token),user);response.addHeader("Set-Cookie",cookie(token,(int)(refreshDays*86400)).toString());}
    private org.springframework.http.ResponseCookie cookie(String value,int seconds){return org.springframework.http.ResponseCookie.from("refreshToken",value).httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api/auth").maxAge(Duration.ofSeconds(seconds)).build();}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}

