package com.cooperativesolutionism.nmsci.integration;

import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.support.NmsciIntegrationTestBase;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPair;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.jayway.jsonpath.JsonPath;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProtocolLifecycleIntegrationTest extends NmsciIntegrationTestBase {

    private final ProtocolMessageBuilder builder = new ProtocolMessageBuilder();

    @Resource
    private BlockInfoRepository blockInfoRepository;

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Resource
    private CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository;

    @Resource
    private TransactionRecordMsgRepository transactionRecordMsgRepository;

    @Resource
    private TransactionMountMsgRepository transactionMountMsgRepository;

    @Resource
    private ConsumeChainRepository consumeChainRepository;

    @Resource
    private ConsumeChainEdgeRepository consumeChainEdgeRepository;

    @Test
    void databaseIsResetWithLatestBlock() {
        var newestBlock = blockInfoRepository.findTopByOrderByHeightDesc();

        assertNotNull(newestBlock);
        assertEquals(0L, newestBlock.getHeight());
        assertEquals(REGISTER_DIFFICULTY_NBITS, newestBlock.getRegisterDifficultyTarget());
        assertEquals(TRANSACTION_DIFFICULTY_NBITS, newestBlock.getTransactionDifficultyTarget());
    }

    @Test
    void systemParamsEndpointReturnsCurrentConfigAndLatestBlock() throws Exception {
        var newestBlock = blockInfoRepository.findTopByOrderByHeightDesc();

        mockMvc.perform(get("/system/params"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.blockVersion").value(3))
                .andExpect(jsonPath("$.data.centralPubkey").value(ByteArrayUtil.bytesToHex(TestKeyPairs.CENTRAL.pubkey())))
                .andExpect(jsonPath("$.data.registerDifficultyTargetNbits").value(REGISTER_DIFFICULTY_NBITS))
                .andExpect(jsonPath("$.data.registerDifficultyTargetNbitsHex").value("0x20ffffff"))
                .andExpect(jsonPath("$.data.transactionDifficultyTargetNbits").value(TRANSACTION_DIFFICULTY_NBITS))
                .andExpect(jsonPath("$.data.transactionDifficultyTargetNbitsHex").value("0x20ffffff"))
                .andExpect(jsonPath("$.data.sourceCodeZipHash").value("0000000000000000000000000000000000000000000000000000000000000000"))
                .andExpect(jsonPath("$.data.latestBlockHeight").value(0))
                .andExpect(jsonPath("$.data.latestBlockHash").value(ByteArrayUtil.bytesToHex(newestBlock.getId())));
    }

    @Test
    void blockChainLastEndpointOmitsRawBytesButKeepsHexMetadata() throws Exception {
        var newestBlock = blockInfoRepository.findTopByOrderByHeightDesc();

        mockMvc.perform(get("/blocks/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.height").exists())
                .andExpect(jsonPath("$.data.id").value(ByteArrayUtil.bytesToHex(newestBlock.getId())))
                .andExpect(jsonPath("$.data.rawBytes").doesNotExist());
    }

    @Test
    void savesFlowNodeRegisterMessage() throws Exception {
        UUID flowNodeId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);

        var saved = flowNodeRegisterMsgRepository.findById(flowNodeId).orElseThrow();
        assertArrayEquals(TestKeyPairs.FLOW_NODE_A.pubkey(), saved.getFlowNodePubkey());
        assertEquals(123, saved.getRawBytes().length);
        assertEquals(32, saved.getTxid().length);
    }

    @Test
    void savesEmpoweredTransactionRecordAndMount() throws Exception {
        UUID flowNodeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID empowerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID recordId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID mountId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);
        sendCentralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A);
        sendTransactionRecord(recordId, 1200L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionMount(mountId, recordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);

        var record = transactionRecordMsgRepository.findById(recordId).orElseThrow();
        var mount = transactionMountMsgRepository.findById(mountId).orElseThrow();

        assertEquals(1200L, record.getAmount());
        assertEquals(335, record.getRawBytes().length);
        assertEquals(recordId, mount.getMountedTransactionRecordId());
        assertEquals(341, mount.getRawBytes().length);
        assertEquals(1, consumeChainRepository.count());
        assertEquals(1, consumeChainEdgeRepository.count());
    }

    @Test
    void queryConsumeChainByMountedTransactionUsesChainSort() throws Exception {
        UUID flowNodeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID empowerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID recordId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID mountId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);
        sendCentralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A);
        sendTransactionRecord(recordId, 1200L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionMount(mountId, recordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);

        mockMvc.perform(get("/consume-chains")
                        .param("mountedTransactionId", mountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.numberOfElements").value(1));
    }

    @Test
    void queryConsumeChainByPubkeyUsesExistingSliceResponseFormat() throws Exception {
        UUID flowNodeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID empowerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID recordId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID mountId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);
        sendCentralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A);
        sendTransactionRecord(recordId, 1200L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionMount(mountId, recordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);

        mockMvc.perform(get("/consume-chains")
                        .param("nodePubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey()))
                        .param("isLoop", "true")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.numberOfElements").value(1));
    }

    @Test
    void queryConsumeChainByIdUsesExistingSliceResponseFormat() throws Exception {
        UUID flowNodeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID empowerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID recordId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID mountId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);
        sendCentralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A);
        sendTransactionRecord(recordId, 1200L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionMount(mountId, recordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);

        mockMvc.perform(get("/consume-chains")
                        .param("nodeId", flowNodeId.toString())
                        .param("isLoop", "true")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.numberOfElements").value(1));
    }

    @Test
    void queryConsumeChainEdgesUsesSliceResponseFormat() throws Exception {
        UUID flowNodeId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID empowerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID recordId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID mountId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);
        sendCentralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A);
        sendTransactionRecord(recordId, 1200L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionMount(mountId, recordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);

        mockMvc.perform(get("/consume-chains/edges")
                        .param("targetPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey()))
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").exists())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.numberOfElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.hasPrevious").value(false));
    }

    @Test
    void queryConsumeChainEdgesReportsHasNextForOneItemPage() throws Exception {
        UUID flowNodeId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID empowerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID firstRecordId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        UUID firstMountId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        UUID secondRecordId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        UUID secondMountId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);
        sendCentralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A);
        sendTransactionRecord(firstRecordId, 1200L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionMount(firstMountId, firstRecordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionRecord(secondRecordId, 800L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionMount(secondMountId, secondRecordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);

        MvcResult firstPage = mockMvc.perform(get("/consume-chains/edges")
                        .param("targetPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey()))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").exists())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.numberOfElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.hasPrevious").value(false))
                .andReturn();

        MvcResult secondPage = mockMvc.perform(get("/consume-chains/edges")
                        .param("targetPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey()))
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").exists())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.numberOfElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.hasPrevious").value(true))
                .andReturn();

        String firstEdgeId = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.data.content[0].id");
        String secondEdgeId = JsonPath.read(secondPage.getResponse().getContentAsString(), "$.data.content[0].id");
        assertNotEquals(firstEdgeId, secondEdgeId);
    }

    @Test
    void returningFlowRateIsAggregatedByDatabase() throws Exception {
        UUID flowNodeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID empowerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID recordId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID mountId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);
        sendCentralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A);
        sendTransactionRecord(recordId, 1200L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionMount(mountId, recordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);

        mockMvc.perform(get("/returning-flow-rates")
                        .param("sourceId", flowNodeId.toString())
                        .param("targetId", flowNodeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.returningFlowRate").value(1.0))
                .andExpect(jsonPath("$.data.loopedAmount").value(1200.0))
                .andExpect(jsonPath("$.data.unloopedAmount").value(0.0));

        mockMvc.perform(get("/returning-flow-rates")
                        .param("targetId", flowNodeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.targetTotalLoopedAmount").value(1200.0))
                .andExpect(jsonPath("$.data.targetTotalUnloopedAmount").value(0.0));
    }

    @Test
    void queryByFlowNodePubkeyReturnsSavedRegisterMessage() throws Exception {
        UUID flowNodeId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);

        mockMvc.perform(get("/flow-node-registrations")
                        .param("flowNodePubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.numberOfElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(flowNodeId.toString()));
    }

    private void sendFlowNodeRegister(UUID id, TestKeyPair flowNode) throws Exception {
        mockMvc.perform(post("/flow-node-registrations")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(id, flowNode, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void sendCentralPubkeyEmpower(UUID id, TestKeyPair flowNode) throws Exception {
        mockMvc.perform(post("/central-pubkey-empowerments")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.centralPubkeyEmpower(id, flowNode, TestKeyPairs.CENTRAL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200));
        assertTrue(centralPubkeyEmpowerMsgRepository.existsById(id));
    }

    private void sendTransactionRecord(UUID id, long amount, TestKeyPair consumeNode, TestKeyPair flowNode) throws Exception {
        mockMvc.perform(post("/transaction-records")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.transactionRecord(id, amount, consumeNode, flowNode, TestKeyPairs.CENTRAL, TRANSACTION_DIFFICULTY_NBITS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void sendTransactionMount(UUID id, UUID recordId, TestKeyPair consumeNode, TestKeyPair flowNode) throws Exception {
        mockMvc.perform(post("/transaction-mounts")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.transactionMount(id, recordId, consumeNode, flowNode, TestKeyPairs.CENTRAL, TRANSACTION_DIFFICULTY_NBITS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200));
    }
}
