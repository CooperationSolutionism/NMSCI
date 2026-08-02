package com.cooperativesolutionism.nmsci.integration;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.support.NmsciIntegrationTestBase;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BlockChainIntegrationTest extends NmsciIntegrationTestBase {

    private final ProtocolMessageBuilder builder = new ProtocolMessageBuilder();

    @Resource
    private BlockChainService blockChainService;

    @Resource
    private NmsciProperties nmsciProperties;

    @Resource
    private BlockInfoRepository blockInfoRepository;

    @Resource
    private MsgAbstractRepository msgAbstractRepository;

    @Test
    void generateBlockPersistsNextBlockMarksMessagesAndWritesFiles() throws Exception {
        UUID flowNodeId = UUID.fromString("11111111-aaaa-1111-aaaa-111111111111");
        var previousBlock = blockInfoRepository.findTopByOrderByHeightDesc();

        mockMvc.perform(post("/flow-node-registrations")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(1L, msgAbstractRepository.countByIsInBlockFalseOrderByConfirmTimestampAsc());

        blockChainService.generateBlock();

        var generatedBlock = blockInfoRepository.findByHeight(1L);
        assertNotNull(generatedBlock);
        assertEquals(1L, generatedBlock.getHeight());
        assertArrayEquals(previousBlock.getId(), generatedBlock.getPreviousBlockHash());
        assertEquals(32, generatedBlock.getId().length);
        assertEquals(32, generatedBlock.getMerkleRoot().length);
        assertEquals(64, generatedBlock.getCentralSignature().length);
        assertEquals("blk00000000.dat", generatedBlock.getDatFilepath());
        assertEquals("source_code_v3.zip", generatedBlock.getSourceCodeZipFilepath());
        assertEquals(0L, msgAbstractRepository.countByIsInBlockFalseOrderByConfirmTimestampAsc());
        assertTrue(msgAbstractRepository.findAll().stream().allMatch(msgAbstract -> Boolean.TRUE.equals(msgAbstract.getIsInBlock())));

        Path datFile = Path.of("target", "nmsci-test-files", "dat", generatedBlock.getDatFilepath());
        Path sourceCodeZip = Path.of("target", "nmsci-test-files", "source-code", generatedBlock.getSourceCodeZipFilepath());
        assertTrue(Files.exists(datFile), "block dat file must be written");
        assertTrue(Files.size(datFile) >= generatedBlock.getRawBytes().length + 12L);
        assertTrue(Files.exists(sourceCodeZip), "source code zip must be copied");
    }

    @Test
    void generateBlockRotatesDatFileUsingPlatformPathSeparator() throws Exception {
        UUID flowNodeId = UUID.fromString("22222222-aaaa-2222-aaaa-222222222222");
        Path datDir = Path.of("target", "nmsci-test-files", "dat");
        Path currentDatFile = datDir.resolve("blk00000000.dat");
        Path nextDatFile = datDir.resolve("blk00000001.dat");
        Files.createDirectories(datDir);
        Files.write(currentDatFile, new byte[]{1});
        Files.deleteIfExists(nextDatFile);
        Object originalBlockDatMaxSize = ReflectionTestUtils.getField(nmsciProperties, "blockDatMaxSize");
        ReflectionTestUtils.setField(nmsciProperties, "blockDatMaxSize", 1L);
        try {
            mockMvc.perform(post("/flow-node-registrations")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(builder.flowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200));

            blockChainService.generateBlock();

            var generatedBlock = blockInfoRepository.findByHeight(1L);
            assertNotNull(generatedBlock);
            assertEquals("blk00000001.dat", generatedBlock.getDatFilepath());
            assertTrue(Files.exists(nextDatFile), "rotated dat file must be written");
        } finally {
            ReflectionTestUtils.setField(nmsciProperties, "blockDatMaxSize", originalBlockDatMaxSize);
        }
    }

    @Test
    void generateBlockKeepsRawBytesWithinBlockMaxSizeIncludingMessageCountFields() throws Exception {
        UUID flowNodeId = UUID.fromString("33333333-aaaa-3333-aaaa-333333333333");
        byte[] flowNodeRegisterMsg = builder.flowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS);
        int blockHeaderSize = (Integer) ReflectionTestUtils.getField(nmsciProperties, "blockHeaderSize");
        long configuredBlockMaxSize = blockHeaderSize + flowNodeRegisterMsg.length;
        Object originalBlockMaxSize = ReflectionTestUtils.getField(nmsciProperties, "blockMaxSize");
        ReflectionTestUtils.setField(nmsciProperties, "blockMaxSize", configuredBlockMaxSize);
        try {
            mockMvc.perform(post("/flow-node-registrations")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(flowNodeRegisterMsg))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(200));

            blockChainService.generateBlock();

            var generatedBlock = blockInfoRepository.findByHeight(1L);
            assertNotNull(generatedBlock);
            assertTrue(generatedBlock.getRawBytes().length <= configuredBlockMaxSize);
            assertEquals(1L, msgAbstractRepository.countByIsInBlockFalseOrderByConfirmTimestampAsc());
        } finally {
            ReflectionTestUtils.setField(nmsciProperties, "blockMaxSize", originalBlockMaxSize);
        }
    }
}
