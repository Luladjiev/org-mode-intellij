package com.luladjiev.orgmode.core

import com.luladjiev.orgmode.core.edit.EditResult
import com.luladjiev.orgmode.core.edit.OrgSnapshot
import com.luladjiev.orgmode.core.edit.StructureEdits
import com.luladjiev.orgmode.core.parser.SyntaxElement
import com.luladjiev.orgmode.core.parser.SyntaxTree
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/** Benchmarks on the generated 1 MB [LargeDocument]: `./gradlew :org-core:jmh`. */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
open class LargeDocumentBenchmark {
    private lateinit var text: String
    private lateinit var snapshot: OrgSnapshot
    private var offset = 0

    @Setup
    fun setUp() {
        text = LargeDocument.text
        snapshot = OrgSnapshot.parse(text)
        offset = LargeDocument.lastTopLevelHeading(text)
    }

    /** The full parse an edit or a fresh file starts from. */
    @Benchmark
    fun parse(): SyntaxElement = SyntaxTree.parse(text)

    /** Move Subtree Up as the command runs it: a snapshot of the document, then the edit. */
    @Benchmark
    fun moveSubtree(): EditResult = StructureEdits.moveSubtreeUp(OrgSnapshot.parse(text), offset)

    /** The edit alone on a parsed snapshot, to separate it from the parse. */
    @Benchmark
    fun moveSubtreeOnSnapshot(): EditResult = StructureEdits.moveSubtreeUp(snapshot, offset)
}
