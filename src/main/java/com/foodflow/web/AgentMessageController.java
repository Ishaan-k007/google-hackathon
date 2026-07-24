package com.foodflow.web;

import com.foodflow.model.AgentMessage;
import com.foodflow.store.AgentMessageStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-messages")
public class AgentMessageController {

    private final AgentMessageStore agentMessageStore;

    public AgentMessageController(AgentMessageStore agentMessageStore) {
        this.agentMessageStore = agentMessageStore;
    }

    @GetMapping
    public Map<String, Object> feed() {
        List<AgentMessage> visible = agentMessageStore.findVisibleNow();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("messages", visible);
        response.put("complete", agentMessageStore.isRunComplete());
        return response;
    }
}
