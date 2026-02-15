package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar

import com.andrei1058.spigot.sidebar.PlaceholderProvider
import com.andrei1058.spigot.sidebar.SidebarLine
import com.andrei1058.spigot.sidebar.WrappedSidebar

/**
 * Concrete sidebar. All the packet work is done by [ModernObjective], [ModernScoreLine] and
 * [ModernTabGroup]; the library base class only needs a subclass to instantiate.
 */
class ModernSidebar(
    title: SidebarLine, lines: Collection<SidebarLine>, placeholders: Collection<PlaceholderProvider>
) : WrappedSidebar(title, lines, placeholders)
