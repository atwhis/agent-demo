package com.atwhis.agentdemo.ai.model;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {
    private final ModelRegistry registry;

    @GetMapping
    public List<ModelDescriptor> models() {
        return registry.descriptors();
    }
}
