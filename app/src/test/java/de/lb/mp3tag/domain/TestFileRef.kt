package de.lb.mp3tag.domain

data class TestFileRef(
    override val name: String,
    override val path: String,
) : FileRef
