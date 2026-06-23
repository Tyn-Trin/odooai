package com.basic.odooai.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.basic.odooai.model.AiProvider;
import com.basic.odooai.model.OdooSession;
import com.basic.odooai.repository.AiProviderRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClaudeService {

        private final AiProviderRepository aiProviderRepository;
        private final OdooQueryService odooQueryService;

        private AnthropicClient client;
        private String cachedModel;

        private static final String SYSTEM_PROMPT = """
                        คุณคือผู้ช่วย AI สำหรับข้อมูลธุรกิจจากระบบ Odoo ตอบเป็นภาษาไทย กระชับและชัดเจน

                        เมื่อต้องการข้อมูลให้ใช้ tool query_odoo พร้อมระบุ model, domain, fields ที่เหมาะสม

                        model ที่ใช้บ่อย:
                        - account.move: บิล/ใบแจ้งหนี้ (move_type: out_invoice=ขาย, in_invoice=ซื้อ)
                        - sale.order: ใบสั่งขาย (state: draft,sale,done,cancel)
                        - purchase.order: ใบสั่งซื้อ (state: draft,purchase,done,cancel)
                        - res.partner: ลูกค้า/ผู้ขาย
                        - stock.picking: การรับ/ส่งสินค้า
                        - account.payment: การชำระเงิน
                        """;

        private static final Tool QUERY_ODOO_TOOL = Tool.builder()
                        .name("query_odoo")
                        .description("ดึงข้อมูลจาก Odoo ด้วย search_read ใช้ตอบคำถามเกี่ยวกับข้อมูลธุรกิจ")
                        .inputSchema(Tool.InputSchema.builder()
                                        .properties(Tool.InputSchema.Properties.builder()
                                                        .putAdditionalProperty("model", JsonValue.from(Map.of(
                                                                        "type", "string",
                                                                        "description",
                                                                        "Odoo model เช่น account.move, sale.order, purchase.order, res.partner")))
                                                        .putAdditionalProperty("domain", JsonValue.from(Map.of(
                                                                        "type", "array",
                                                                        "description",
                                                                        "Odoo domain filter เช่น [[\"state\",\"=\",\"posted\"]]")))
                                                        .putAdditionalProperty("fields", JsonValue.from(Map.of(
                                                                        "type", "array",
                                                                        "items", Map.of("type", "string"),
                                                                        "description",
                                                                        "Fields ที่ต้องการ เช่น [\"name\",\"partner_id\",\"amount_total\"]")))
                                                        .putAdditionalProperty("limit", JsonValue.from(Map.of(
                                                                        "type", "integer",
                                                                        "description",
                                                                        "จำนวนแถวสูงสุด ถ้าไม่ระบุใช้ 100")))
                                                        .build())
                                        .required(List.of("model", "domain", "fields"))
                                        .build())
                        .build();

        public ClaudeService(AiProviderRepository aiProviderRepository, OdooQueryService odooQueryService) {
                this.aiProviderRepository = aiProviderRepository;
                this.odooQueryService = odooQueryService;
        }

        @PostConstruct
        public void reload() {
                AiProvider provider = aiProviderRepository.findByActive(true).orElse(null);
                if (provider != null && provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
                        client = AnthropicOkHttpClient.builder().apiKey(provider.getApiKey()).build();
                        cachedModel = provider.getModel();
                } else {
                        client = null;
                        cachedModel = null;
                }
        }

        public String ask(String question, OdooSession session) {
                if (client == null) {
                        return "กรุณาตั้งค่า Claude API Key ในหน้า Settings ก่อนใช้งาน";
                }

                List<MessageParam> messages = new ArrayList<>();
                messages.add(MessageParam.builder()
                                .role(MessageParam.Role.USER)
                                .content(question)
                                .build());

                while (true) {
                        MessageCreateParams params = MessageCreateParams.builder()
                                        .model(Model.of(cachedModel))
                                        .maxTokens(4096L)
                                        .system(SYSTEM_PROMPT)
                                        .addTool(QUERY_ODOO_TOOL)
                                        .messages(messages)
                                        .build();

                        Message response = client.messages().create(params);

                        List<ToolUseBlock> toolUseBlocks = response.content().stream()
                                        .flatMap(block -> block.toolUse().stream())
                                        .collect(Collectors.toList());

                        if (toolUseBlocks.isEmpty()) {
                                return response.content().stream()
                                                .flatMap(block -> block.text().stream())
                                                .map(TextBlock::text)
                                                .collect(Collectors.joining());
                        }

                        // เพิ่ม assistant message (มี tool_use blocks) เข้า conversation
                        List<ContentBlockParam> assistantContent = new ArrayList<>();
                        for (ContentBlock block : response.content()) {
                                if (block.text().isPresent()) {
                                        assistantContent.add(ContentBlockParam.ofText(
                                                        TextBlockParam.builder().text(block.text().get().text())
                                                                        .build()));
                                } else if (block.toolUse().isPresent()) {
                                        ToolUseBlock tu = block.toolUse().get();
                                        // JsonValue extends JsonField as raw type — asObject() returns raw Optional →
                                        // must cast
                                        @SuppressWarnings("unchecked")
                                        Map<String, JsonValue> inputProps = (Map<String, JsonValue>) tu._input()
                                                        .asObject().orElse(Map.of());
                                        ToolUseBlockParam.Input input = ToolUseBlockParam.Input.builder()
                                                        .additionalProperties(inputProps)
                                                        .build();
                                        assistantContent.add(ContentBlockParam.ofToolUse(
                                                        ToolUseBlockParam.builder()
                                                                        .id(tu.id())
                                                                        .name(tu.name())
                                                                        .input(input)
                                                                        .build()));
                                }
                        }
                        messages.add(MessageParam.builder()
                                        .role(MessageParam.Role.ASSISTANT)
                                        .contentOfBlockParams(assistantContent)
                                        .build());

                        // Execute tools แล้วส่งผลกลับให้ Claude
                        List<ContentBlockParam> toolResults = new ArrayList<>();
                        for (ToolUseBlock tu : toolUseBlocks) {
                                String result = executeQueryOdoo(tu._input(), session);
                                toolResults.add(ContentBlockParam.ofToolResult(
                                                ToolResultBlockParam.builder()
                                                                .toolUseId(tu.id())
                                                                .content(result)
                                                                .build()));
                        }
                        messages.add(MessageParam.builder()
                                        .role(MessageParam.Role.USER)
                                        .contentOfBlockParams(toolResults)
                                        .build());
                }
        }

        @SuppressWarnings("unchecked")
        private String executeQueryOdoo(JsonValue input, OdooSession session) {
                // convert() ใช้ Jackson ภายใน คืน plain Java types
                // (String/Number/Boolean/List/Map)
                Map<String, Object> inputMap = input.convert(new TypeReference<Map<String, Object>>() {
                });

                String model = (String) inputMap.getOrDefault("model", "");
                List<String> fields = (List<String>) inputMap.getOrDefault("fields", List.of());
                int limit = inputMap.containsKey("limit")
                                ? ((Number) inputMap.get("limit")).intValue()
                                : 100;
                Object domain = inputMap.getOrDefault("domain", List.of());

                return odooQueryService.searchRead(session, model, domain, fields, limit);
        }
}
