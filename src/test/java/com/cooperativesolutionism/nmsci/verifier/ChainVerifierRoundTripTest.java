package com.cooperativesolutionism.nmsci.verifier;

import com.cooperativesolutionism.nmsci.block.AssembledBlock;
import com.cooperativesolutionism.nmsci.block.BlockAssembler;
import com.cooperativesolutionism.nmsci.block.BlockMessagePayloadFetcher;
import com.cooperativesolutionism.nmsci.block.SelectedBlockMessages;
import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.MessagePayloadProjection;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPair;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.Security;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 端到端往返测试：用真实 {@link BlockAssembler} 装配区块（含真实 PoW + 真实签名），再交由验证器核验。
 * 由于使用的是生产装配路径，本测试能权威性地捕获验证器字节布局或算法与写入侧的任何偏差。
 */
class ChainVerifierRoundTripTest {

    private static final int EASY_NBITS = 0x20ffffff;

    private final ProtocolMessageBuilder builder = new ProtocolMessageBuilder();
    private final ChainVerifier verifier = new ChainVerifier();

    @BeforeAll
    static void addBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void verifiesGenesisRegisterOnlyBlockProducedByRealAssembler() {
        UUID msgId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AssembledBlock block = assembleRegisterBlock(null, TestKeyPairs.FLOW_NODE_A, msgId);

        List<ParsedBlock> blocks = DatBlockReader.readConcatenated(block.getDatBytes(), "blk00000000.dat");
        assertEquals(1, blocks.size());
        ParsedBlock parsed = blocks.get(0);
        assertEquals(1, parsed.version());
        assertEquals(0L, parsed.height());
        assertEquals(1, parsed.messages().size());
        assertEquals(msgId, parsed.messages().get(0).id());
        assertArrayEquals(block.getBlockInfo().getId(), parsed.blockId());

        ChainVerificationResult result = verifier.verify(blocks, optionsWithCentral());
        assertTrue(result.ok(), () -> "期望验证通过，但有失败:\n" + result.render());
    }

    @Test
    void verifiesTwoLinkedRegisterBlocks() {
        AssembledBlock genesis = assembleRegisterBlock(
                null, TestKeyPairs.FLOW_NODE_A, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        AssembledBlock second = assembleRegisterBlock(
                genesis.getBlockInfo(), TestKeyPairs.FLOW_NODE_B, UUID.fromString("22222222-2222-2222-2222-222222222222"));

        ByteArrayOutputStream concatenated = new ByteArrayOutputStream();
        concatenated.writeBytes(genesis.getDatBytes());
        concatenated.writeBytes(second.getDatBytes());

        List<ParsedBlock> blocks = DatBlockReader.readConcatenated(concatenated.toByteArray(), "blk00000000.dat");
        assertEquals(2, blocks.size());
        assertEquals(0L, blocks.get(0).height());
        assertEquals(1L, blocks.get(1).height());

        ChainVerificationResult result = verifier.verify(blocks, optionsWithCentral());
        assertTrue(result.ok(), () -> "期望两区块链验证通过，但有失败:\n" + result.render());
    }

    @Test
    void acceptsAndLinksVersionUpgradeChain() {
        // 合法升级：v1 → v2 → v3；生产护栏允许版本单调递增，当前验证器应支持到 v3。
        AssembledBlock genesis = assembleRegisterBlock(
                null, TestKeyPairs.FLOW_NODE_A, UUID.fromString("11111111-1111-1111-1111-111111111111"), EASY_NBITS, 1);
        AssembledBlock v2 = assembleRegisterBlock(
                genesis.getBlockInfo(), TestKeyPairs.FLOW_NODE_B, UUID.fromString("22222222-2222-2222-2222-222222222222"), EASY_NBITS, 2);
        AssembledBlock v3 = assembleRegisterBlock(
                v2.getBlockInfo(), TestKeyPairs.CONSUME_NODE_A, UUID.fromString("33333333-3333-3333-3333-333333333333"), EASY_NBITS, 3);

        ByteArrayOutputStream concatenated = new ByteArrayOutputStream();
        concatenated.writeBytes(genesis.getDatBytes());
        concatenated.writeBytes(v2.getDatBytes());
        concatenated.writeBytes(v3.getDatBytes());

        List<ParsedBlock> blocks = DatBlockReader.readConcatenated(concatenated.toByteArray(), "blk00000000.dat");
        assertEquals(1, blocks.get(0).version());
        assertEquals(2, blocks.get(1).version());
        assertEquals(3, blocks.get(2).version());

        ChainVerificationResult result = verifier.verify(blocks, optionsWithCentral());
        assertTrue(result.ok(), () -> "期望 v1→v2→v3 升级链在当前验证器中通过，但有失败:\n" + result.render());
    }

    @Test
    void rejectsBlockVersionAboveVerifierSupport() {
        // v4 区块但验证器默认仅支持到 v3 → 「区块版本号」应判为过新。
        AssembledBlock v4 = assembleRegisterBlock(
                null, TestKeyPairs.FLOW_NODE_A, UUID.fromString("11111111-1111-1111-1111-111111111111"), EASY_NBITS, 4);

        ChainVerificationResult result = verifier.verify(
                DatBlockReader.readConcatenated(v4.getDatBytes(), "blk00000000.dat"), optionsWithCentral());
        assertFalse(result.ok());
        assertTrue(result.allFailures().stream().anyMatch(check -> check.name().equals("区块版本号")),
                () -> "应检出版本过新:\n" + result.render());
    }

    @Test
    void detectsVersionDowngradeAlongChain() {
        // 生产护栏使「合法链接的降级链」无法被装配产出，故用两个独立创世块拼成 [v2, v1] 构造离线降级 .dat。
        // 链接/高度会附带失败，但通过断言「次块的区块版本单调性」为 FAILED、且两块版本号检查均 PASSED（max=2），
        // 把失败原因精确锚定到版本降级本身，而非附带的链接失败。
        AssembledBlock v2 = assembleRegisterBlock(
                null, TestKeyPairs.FLOW_NODE_A, UUID.fromString("11111111-1111-1111-1111-111111111111"), EASY_NBITS, 2);
        AssembledBlock v1 = assembleRegisterBlock(
                null, TestKeyPairs.FLOW_NODE_B, UUID.fromString("22222222-2222-2222-2222-222222222222"), EASY_NBITS, 1);

        ByteArrayOutputStream concatenated = new ByteArrayOutputStream();
        concatenated.writeBytes(v2.getDatBytes());
        concatenated.writeBytes(v1.getDatBytes());

        ChainVerificationResult result = verifier.verify(
                DatBlockReader.readConcatenated(concatenated.toByteArray(), "blk00000000.dat"), optionsWithCentral(2));
        assertFalse(result.ok());
        assertTrue(result.blocks().get(1).checks().stream()
                        .anyMatch(check -> check.name().equals("区块版本单调性") && check.status() == CheckResult.Status.FAILED),
                () -> "次块应因版本降级被「区块版本单调性」判失败:\n" + result.render());
        assertTrue(result.blocks().stream().flatMap(block -> block.checks().stream())
                        .filter(check -> check.name().equals("区块版本号"))
                        .allMatch(check -> check.status() == CheckResult.Status.PASSED),
                () -> "两块版本号均应在支持范围[1,2]内通过，确认失败仅来自单调性:\n" + result.render());
    }

    @Test
    void productionAssemblerRefusesToMintDowngradeBlock() {
        // 生产降级护栏：在 v2 区块之后用 v1 配置出块应被拒绝。
        AssembledBlock v2 = assembleRegisterBlock(
                null, TestKeyPairs.FLOW_NODE_A, UUID.fromString("11111111-1111-1111-1111-111111111111"), EASY_NBITS, 2);

        assertThrows(IllegalStateException.class, () -> assembleRegisterBlock(
                v2.getBlockInfo(), TestKeyPairs.FLOW_NODE_B, UUID.fromString("22222222-2222-2222-2222-222222222222"), EASY_NBITS, 1));
    }

    @Test
    void detectsMessageDifficultyNotMatchingPredecessorBlock() {
        // 创世块难度 EASY；次块的注册消息携带不同难度 → 应被「消息难度=入账难度」检出（与前区块难度比对）
        AssembledBlock genesis = assembleRegisterBlock(
                null, TestKeyPairs.FLOW_NODE_A, UUID.fromString("11111111-1111-1111-1111-111111111111"));
        AssembledBlock second = assembleRegisterBlock(
                genesis.getBlockInfo(), TestKeyPairs.FLOW_NODE_B,
                UUID.fromString("22222222-2222-2222-2222-222222222222"), 0x20fffffe);

        ByteArrayOutputStream concatenated = new ByteArrayOutputStream();
        concatenated.writeBytes(genesis.getDatBytes());
        concatenated.writeBytes(second.getDatBytes());

        ChainVerificationResult result = verifier.verify(
                DatBlockReader.readConcatenated(concatenated.toByteArray(), "blk00000000.dat"), optionsWithCentral());
        assertFalse(result.ok());
        assertTrue(result.allFailures().stream().anyMatch(check -> check.name().equals("消息难度=入账难度")),
                () -> "应检出消息难度与前区块不一致:\n" + result.render());
    }

    @Test
    void rejectsCorruptedMagic() {
        AssembledBlock block = assembleRegisterBlock(null, TestKeyPairs.FLOW_NODE_A,
                UUID.fromString("11111111-1111-1111-1111-111111111111"));
        byte[] datBytes = block.getDatBytes();
        datBytes[0] ^= 0x01;

        assertThrows(BlockFormatException.class, () -> DatBlockReader.readConcatenated(datBytes, "blk.dat"));
    }

    @Test
    void detectsTamperedMerkleRoot() {
        AssembledBlock block = assembleRegisterBlock(null, TestKeyPairs.FLOW_NODE_A,
                UUID.fromString("11111111-1111-1111-1111-111111111111"));
        byte[] datBytes = block.getDatBytes();
        // 区块头默克尔根位于原始区块偏移 76，.dat 记录前缀 12 字节 → 偏移 88
        datBytes[12 + 76] ^= 0x01;

        ChainVerificationResult result = verifier.verify(
                DatBlockReader.readConcatenated(datBytes, "blk.dat"), optionsWithCentral());
        assertFalse(result.ok());
        assertTrue(result.allFailures().stream().anyMatch(check -> check.name().equals("默克尔根")),
                () -> "应检出默克尔根失败:\n" + result.render());
    }

    @Test
    void detectsTamperedMemberSignature() {
        AssembledBlock block = assembleRegisterBlock(null, TestKeyPairs.FLOW_NODE_A,
                UUID.fromString("11111111-1111-1111-1111-111111111111"));
        byte[] datBytes = block.getDatBytes();
        // 定位注册记录末字节（其成员签名末字节）：12字节.dat前缀 + 229字节头 + 8字节段计数 + 记录长度-1
        int recordStart = 12 + ParsedBlock.HEADER_SIZE + Long.BYTES;
        int lastSignatureByte = recordStart + MsgTypeEnum.FlowNodeRegisterMsg.getSize() - 1;
        datBytes[lastSignatureByte] ^= 0x01;

        ChainVerificationResult result = verifier.verify(
                DatBlockReader.readConcatenated(datBytes, "blk.dat"), optionsWithCentral());
        assertFalse(result.ok());
        assertTrue(result.allFailures().stream().anyMatch(check -> check.name().equals("成员签名验证")),
                () -> "应检出成员签名验证失败:\n" + result.render());
    }

    @Test
    void detectsUnexpectedCentralPubkeyWhileSignatureStillValid() {
        AssembledBlock block = assembleRegisterBlock(null, TestKeyPairs.FLOW_NODE_A,
                UUID.fromString("11111111-1111-1111-1111-111111111111"));
        VerifierOptions options = VerifierOptions.builder()
                .expectedCentralPubkey(TestKeyPairs.FLOW_NODE_B.pubkey())
                .includeStatefulReplay(true)
                .build();

        ChainVerificationResult result = verifier.verify(
                DatBlockReader.readConcatenated(block.getDatBytes(), "blk.dat"), options);
        assertFalse(result.ok());
        assertTrue(result.allFailures().stream().anyMatch(check -> check.name().equals("中心公钥符合期望")));
        // 区块自身签名在其头部公钥下仍应有效
        assertTrue(result.blocks().get(0).checks().stream()
                .anyMatch(check -> check.name().equals("区块中心签名") && check.status() == CheckResult.Status.PASSED));
    }

    private VerifierOptions optionsWithCentral() {
        return VerifierOptions.builder()
                .expectedCentralPubkey(TestKeyPairs.CENTRAL.pubkey())
                .includeStatefulReplay(true)
                .build();
    }

    private VerifierOptions optionsWithCentral(int maxSupportedBlockVersion) {
        return VerifierOptions.builder()
                .expectedCentralPubkey(TestKeyPairs.CENTRAL.pubkey())
                .includeStatefulReplay(true)
                .maxSupportedBlockVersion(maxSupportedBlockVersion)
                .build();
    }

    private AssembledBlock assembleRegisterBlock(BlockInfo previousBlock, TestKeyPair flowNode, UUID msgId) {
        return assembleRegisterBlock(previousBlock, flowNode, msgId, EASY_NBITS, 1);
    }

    private AssembledBlock assembleRegisterBlock(BlockInfo previousBlock, TestKeyPair flowNode, UUID msgId, int registerMsgNbits) {
        return assembleRegisterBlock(previousBlock, flowNode, msgId, registerMsgNbits, 1);
    }

    private AssembledBlock assembleRegisterBlock(
            BlockInfo previousBlock, TestKeyPair flowNode, UUID msgId, int registerMsgNbits, int blockVersion) {
        byte[] rawBytes = builder.flowNodeRegister(msgId, flowNode, registerMsgNbits);
        byte[] txid = MerkleTreeUtil.calcTxid(rawBytes);

        FlowNodeRegisterMsgRepository registerRepository = mock(FlowNodeRegisterMsgRepository.class);
        when(registerRepository.findPayloadByIdIn(List.of(msgId)))
                .thenReturn(List.of(new StubProjection(msgId, rawBytes, txid)));

        BlockAssembler assembler = new BlockAssembler(properties(blockVersion), new BlockMessagePayloadFetcher(
                registerRepository,
                mock(CentralPubkeyEmpowerMsgRepository.class),
                mock(CentralPubkeyLockedMsgRepository.class),
                mock(FlowNodeLockedMsgRepository.class),
                mock(TransactionRecordMsgRepository.class),
                mock(TransactionMountMsgRepository.class)
        ));

        return assembler.assemble(previousBlock, selected(msgId));
    }

    private static NmsciProperties properties() {
        return properties(1);
    }

    private static NmsciProperties properties(int blockVersion) {
        NmsciProperties properties = new NmsciProperties();
        properties.setBlockVersion(blockVersion);
        properties.setBlockHeaderSize(229);
        properties.setSourceCodeZipHash("0000000000000000000000000000000000000000000000000000000000000000");
        properties.setRegisterDifficultyTargetNbits(EASY_NBITS);
        properties.setTransactionDifficultyTargetNbits(EASY_NBITS);
        NmsciProperties.CentralKeyPair centralKeyPair = new NmsciProperties.CentralKeyPair();
        centralKeyPair.setPubkey(TestKeyPairs.CENTRAL.pubkeyBase64());
        centralKeyPair.setPrikey(TestKeyPairs.CENTRAL.prikeyBase64());
        properties.setCentralKeyPair(centralKeyPair);
        return properties;
    }

    private static SelectedBlockMessages selected(UUID msgId) {
        Map<MsgTypeEnum, List<MsgAbstract>> messagesByType = new LinkedHashMap<>();
        for (MsgTypeEnum msgType : MsgTypeEnum.values()) {
            messagesByType.put(msgType, new ArrayList<>());
        }
        MsgAbstract msgAbstract = new MsgAbstract();
        msgAbstract.setId(new byte[]{1});
        msgAbstract.setMsgId(msgId);
        msgAbstract.setMsgType(MsgTypeEnum.FlowNodeRegisterMsg.getValue());
        msgAbstract.setConfirmTimestamp(0L);
        msgAbstract.setIsInBlock(false);
        messagesByType.get(MsgTypeEnum.FlowNodeRegisterMsg).add(msgAbstract);
        return new SelectedBlockMessages(messagesByType, 0L);
    }

    private record StubProjection(UUID id, byte[] rawBytes, byte[] txid) implements MessagePayloadProjection {
        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public byte[] getRawBytes() {
            return rawBytes;
        }

        @Override
        public byte[] getTxid() {
            return txid;
        }
    }
}
