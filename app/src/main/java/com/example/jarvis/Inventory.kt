package com.example.jarvis

import android.content.Context
import java.text.Normalizer
import java.util.Locale

data class StockItem(val name: String, val quantity: Int)

object Inventory {
    fun respond(ctx: Context, text: String): String? {
        val command = normalize(text).trim().trimEnd('.', '!', '?')
        val items = Prefs.getInventory(ctx).toMutableList()

        if (command in setOf(
                "liste mon stock",
                "montre mon stock",
                "affiche mon stock",
                "liste mon inventaire",
                "montre mon inventaire",
                "affiche mon inventaire",
                "qu est ce qu il me reste"
            )
        ) {
            return if (items.isEmpty()) {
                "Ton stock est vide. Tu peux dire « ajoute 2 pommes au stock » pour commencer."
            } else {
                "Dans ton stock : " + items.sortedBy { normalize(it.name) }
                    .joinToString(", ") { "${it.quantity} ${it.name}" } + "."
            }
        }

        val countMatch = Regex(
            "^(?:combien (?:de|d )|quelle quantite de |combien ai je de )(.+?)(?: en stock| dans le stock)?$"
        ).matchEntire(command)
        if (countMatch != null) {
            val item = find(items, countMatch.groupValues[1])
            return if (item == null) {
                "Je n'ai pas trouvé cet article dans ton stock."
            } else {
                "Il te reste ${item.quantity} ${item.name}."
            }
        }

        val setMatch = Regex(
            "^(?:fixe|regle|change la quantite de|mets a jour la quantite de) (.+?) a (\\d+)$"
        ).matchEntire(command)
        if (setMatch != null) {
            val name = cleanName(setMatch.groupValues[1])
            val quantity = setMatch.groupValues[2].toIntOrNull() ?: return null
            if (name.isBlank()) return null
            saveQuantity(ctx, items, name, quantity)
            return quantityAnswer(name, quantity)
        }

        val addMatch = Regex(
            "^ajoute\\s+(?:(\\d+)\\s+)?(.+?)(?:\\s+(?:au|dans le|en)\\s+(?:stock|inventaire))?$"
        ).matchEntire(command)
        if (addMatch != null) {
            val name = cleanName(addMatch.groupValues[2])
            val amount = addMatch.groupValues[1].toIntOrNull() ?: 1
            if (name.isBlank() || amount <= 0) return null
            val existing = find(items, name)
            if (amount > Int.MAX_VALUE - (existing?.quantity ?: 0)) {
                return "Cette quantité est trop grande pour être enregistrée."
            }
            val quantity = (existing?.quantity ?: 0) + amount
            saveQuantity(ctx, items, existing?.name ?: name, quantity)
            return "J'ajoute $amount à ton stock. Il y en a maintenant $quantity."
        }

        val removeMatch = Regex(
            "^(retire|enleve|supprime|efface)\\s+(?:(\\d+)\\s+)?(.+?)(?:\\s+du\\s+(?:stock|inventaire))?$"
        ).matchEntire(command)
        if (removeMatch != null) {
            val verb = removeMatch.groupValues[1]
            val name = cleanName(removeMatch.groupValues[3])
            if (name.isBlank()) return null
            val existing = find(items, name)
                ?: return "Je n'ai pas trouvé cet article dans ton stock."
            if ((verb == "supprime" || verb == "efface") && removeMatch.groupValues[2].isBlank()) {
                Prefs.setInventory(ctx, items.filterNot { it.name == existing.name })
                return "J'ai supprimé ${existing.name} de ton stock."
            }
            val amount = removeMatch.groupValues[2].toIntOrNull() ?: 1
            if (amount <= 0) return null
            val quantity = (existing.quantity - amount).coerceAtLeast(0)
            saveQuantity(ctx, items, existing.name, quantity)
            return if (quantity == 0) {
                "J'ai retiré ${existing.name} de ton stock."
            } else {
                "Il te reste $quantity ${existing.name}."
            }
        }

        return null
    }

    fun setQuantity(ctx: Context, items: List<StockItem>, name: String, quantity: Int) {
        saveQuantity(ctx, items, cleanName(name), quantity.coerceAtLeast(0))
    }

    private fun saveQuantity(ctx: Context, items: List<StockItem>, name: String, quantity: Int) {
        val existing = find(items, name)
        val updated = items.filterNot { existing != null && it.name == existing.name }.toMutableList()
        if (quantity > 0) updated.add(StockItem(existing?.name ?: name, quantity))
        Prefs.setInventory(ctx, updated.sortedBy { normalize(it.name) })
    }

    private fun quantityAnswer(name: String, quantity: Int): String =
        if (quantity == 0) "J'ai retiré $name de ton stock."
        else "Le stock de $name est maintenant de $quantity."

    private fun find(items: List<StockItem>, name: String): StockItem? {
        val key = normalize(cleanName(name))
        return items.firstOrNull { normalize(it.name) == key }
            ?: items.firstOrNull { singular(normalize(it.name)) == singular(key) }
    }

    private fun cleanName(value: String): String =
        value.trim().replace(Regex("^(?:des|du|de la|de l |d |les|la|le|l )"), "").trim()

    private fun singular(value: String): String = value.removeSuffix("s")

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("['’]"), " ")
            .replace(Regex("\\s+"), " ")
}
