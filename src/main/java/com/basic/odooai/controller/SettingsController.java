package com.basic.odooai.controller;

import com.basic.odooai.model.AiProvider;
import com.basic.odooai.repository.AiProviderRepository;
import com.basic.odooai.service.ClaudeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final AiProviderRepository aiProviderRepository;
    private final ClaudeService claudeService;

    public SettingsController(AiProviderRepository aiProviderRepository, ClaudeService claudeService) {
        this.aiProviderRepository = aiProviderRepository;
        this.claudeService = claudeService;
    }

    @GetMapping
    public String settingsPage(Model model) {
        List<AiProvider> providers = aiProviderRepository.findAll();
        model.addAttribute("providers", providers);
        model.addAttribute("newProvider", new AiProvider());
        return "settings";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute AiProvider provider) {
        if (provider.isActive()) {
            aiProviderRepository.findAll().forEach(p -> {
                p.setActive(false);
                aiProviderRepository.save(p);
            });
        }
        aiProviderRepository.save(provider);
        claudeService.reload();
        return "redirect:/settings";
    }

    @PostMapping("/activate/{id}")
    public String activate(@PathVariable Long id) {
        aiProviderRepository.findAll().forEach(p -> {
            p.setActive(p.getId().equals(id));
            aiProviderRepository.save(p);
        });
        claudeService.reload();
        return "redirect:/settings";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        aiProviderRepository.deleteById(id);
        claudeService.reload();
        return "redirect:/settings";
    }
}
