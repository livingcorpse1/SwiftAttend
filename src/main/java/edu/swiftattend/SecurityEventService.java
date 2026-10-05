package edu.swiftattend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class SecurityEventService {
    private final JdbcTemplate db; SecurityEventService(JdbcTemplate db){this.db=db;}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(UUID actor,UUID session,String type,String detail,String ip){db.update("INSERT INTO security_event(id,actor_id,session_id,event_type,detail,ip_address) VALUES(?,?,?,?,?,?)",UUID.randomUUID(),actor,session,type,detail,ip);}
}
