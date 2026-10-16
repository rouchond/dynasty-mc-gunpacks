Nexus supplies patch for Dynasty MC 1.11

ReserveValues.java and the native item tags define the expanded supply rules. ReserveSupplyChecks.java is the offline test runner used with the project Minecraft/NeoForge runtime. changes.diff records the development-source changes. MainSupplyScreenPatch.java applies only the supply tooltip additions to the main-profile screen, preserving its older commerce code and all 39 unrelated methods. These are patch sources, not a standalone copy of the entire mod project.

Main baseline jar SHA256: DE16B53D0DAAF7948093487181B6389157AB7367E99BACB533DE9ACC0334EAC7
Released jar SHA256: 0DBDEF243BCAD1A81D2689F0882782F5581C6521C6C3B8778E759709F3501059

Validation: 2,919 development checks; 61 supply checks rerun against the main-profile jar; ASM structure validation; 123 unrelated main jar entries preserved. Full modpack export preserves 4,471 entries and all five gunpacks from public 1.1, changing only pack version and settlement jar. In-game acceptance remains pending.
