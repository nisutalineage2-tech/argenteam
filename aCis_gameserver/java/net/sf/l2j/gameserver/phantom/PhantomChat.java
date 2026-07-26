package net.sf.l2j.gameserver.phantom;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;

public final class PhantomChat
{
	private static final CLogger LOGGER = new CLogger(PhantomChat.class.getName());
	private static final Pattern TEXT_PATTERN = Pattern.compile("\\\"text\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\\\\\"])*)\\\"");
	private static final Map<Integer, Long> COOLDOWNS = new ConcurrentHashMap<>();
	private static final AtomicInteger DAILY_USAGE = new AtomicInteger();
	private static volatile LocalDate USAGE_DAY = LocalDate.now();
	
	private PhantomChat()
	{
	}
	
	public static boolean onWhisper(Player sender, Player phantom, String text)
	{
		if (sender == null || phantom == null || text == null || text.isBlank())
			return false;
		
		sender.sendPacket(new CreatureSay(sender.getObjectId(), SayType.TELL, "->" + phantom.getName(), text));
		PhantomLog.info("Whisper received for phantom " + phantom.getName() + " from " + sender.getName() + ".");
		
		if (!PhantomConfig.phantomChatEnabled())
		{
			PhantomLog.warn("Gemini skipped for " + phantom.getName() + ": PhantomChatEnabled is false.");
			sendFallback(sender, phantom, text);
			return true;
		}
		
		if (!"Gemini".equalsIgnoreCase(PhantomConfig.phantomChatProvider()))
		{
			PhantomLog.warn("Gemini skipped for " + phantom.getName() + ": provider is " + PhantomConfig.phantomChatProvider() + ".");
			sendFallback(sender, phantom, text);
			return true;
		}
		
		final long now = System.currentTimeMillis();
		final Long nextAllowed = COOLDOWNS.get(phantom.getObjectId());
		if (nextAllowed != null && now < nextAllowed)
		{
			PhantomLog.warn("Gemini skipped for " + phantom.getName() + ": cooldown " + (nextAllowed - now) + "ms remaining on tier " + PhantomConfig.geminiApiTier() + ".");
			sendFallback(sender, phantom, text);
			return true;
		}
		COOLDOWNS.put(phantom.getObjectId(), now + PhantomConfig.geminiCooldownMs());
		
		if (!consumeDailyQuota())
		{
			PhantomLog.warn("Gemini skipped for " + phantom.getName() + ": daily limit reached for tier " + PhantomConfig.geminiApiTier() + ".");
			sendFallback(sender, phantom, text);
			return true;
		}
		
		ThreadPool.execute(() -> replyWithGemini(sender, phantom, text));
		return true;
	}
	
	private static void replyWithGemini(Player sender, Player phantom, String text)
	{
		String answer = null;
		try
		{
			answer = callGemini(phantom, sender, text);
		}
		catch (Exception e)
		{
			LOGGER.warn("Gemini phantom chat failed for {}.", e, phantom.getName());
			PhantomLog.error("Gemini failed for " + phantom.getName(), e);
		}
		
		if (answer == null || answer.isBlank())
		{
			PhantomLog.warn("Gemini returned empty text for " + phantom.getName() + "; using fallback.");
			answer = fallbackFor(text);
		}
		else
			PhantomLog.info("Gemini reply OK for " + phantom.getName() + ".");
		
		answer = trimForChat(answer);
		if (sender.isOnline() && phantom.isOnline())
			sender.sendPacket(new CreatureSay(phantom.getObjectId(), SayType.TELL, phantom.getName(), answer));
	}
	
	private static String callGemini(Player phantom, Player sender, String text) throws Exception
	{
		final String apiKey = PhantomConfig.geminiApiKey();
		if (apiKey == null || apiKey.isBlank())
		{
			PhantomLog.warn("Gemini API key missing for tier " + PhantomConfig.geminiApiTier());
			return fallbackFor(text);
		}
		
		final String endpoint = String.format(PhantomConfig.geminiEndpoint(), urlEncode(PhantomConfig.geminiModel()));
		final HttpURLConnection con = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
		con.setRequestMethod("POST");
		con.setConnectTimeout(PhantomConfig.geminiTimeoutMs());
		con.setReadTimeout(PhantomConfig.geminiTimeoutMs());
		con.setDoOutput(true);
		con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		con.setRequestProperty("x-goog-api-key", apiKey);
		
		final String prompt = PhantomConfig.phantomChatPrompt() + "\nTu personaje se llama " + phantom.getName() + ", nivel " + phantom.getStatus().getLevel() + ". Te escribe " + sender.getName() + ": " + text;
		final String body = "{\"contents\":[{\"parts\":[{\"text\":\"" + json(prompt) + "\"}]}],\"generationConfig\":{\"temperature\":" + PhantomConfig.geminiTemperature() + ",\"maxOutputTokens\":80}}";
		try (OutputStream os = con.getOutputStream())
		{
			os.write(body.getBytes(StandardCharsets.UTF_8));
		}
		
		final int code = con.getResponseCode();
		final byte[] bytes = ((code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream()).readAllBytes();
		final String response = new String(bytes, StandardCharsets.UTF_8);
		if (code < 200 || code >= 300)
		{
			LOGGER.warn("Gemini phantom chat HTTP {}: {}", code, response);
			PhantomLog.warn("Gemini HTTP " + code + " for " + phantom.getName() + ": " + response);
			return fallbackFor(text);
		}
		return extractText(response);
	}
	
	private static boolean consumeDailyQuota()
	{
		final LocalDate today = LocalDate.now();
		if (!today.equals(USAGE_DAY))
		{
			USAGE_DAY = today;
			DAILY_USAGE.set(0);
		}
		
		final int limit = PhantomConfig.geminiDailyLimit();
		if (limit <= 0)
			return false;
		
		return DAILY_USAGE.incrementAndGet() <= limit;
	}
	
	private static void sendFallback(Player sender, Player phantom, String text)
	{
		ThreadPool.schedule(() ->
		{
			if (sender.isOnline() && phantom.isOnline())
				sender.sendPacket(new CreatureSay(phantom.getObjectId(), SayType.TELL, phantom.getName(), trimForChat(fallbackFor(text))));
		}, 1200);
	}
	
	private static String fallbackFor(String text)
	{
		return switch (detectLanguage(text))
		{
			case "es" -> "Disculpa, ahora no puedo. Estoy ocupado.";
			case "pt" -> "Desculpa, agora nao posso. Estou ocupado.";
			case "fr" -> "Desole, je ne peux pas maintenant. Je suis occupe.";
			case "de" -> "Sorry, ich kann gerade nicht. Ich bin beschaeftigt.";
			case "it" -> "Scusa, ora non posso. Sono occupato.";
			case "pl" -> "Przepraszam, teraz nie moge. Jestem zajety.";
			case "tr" -> "Uzgunum, su an konusamam. Mesgulum.";
			case "nl" -> "Sorry, ik kan nu niet praten. Ik ben bezig.";
			case "id" -> "Maaf, sekarang tidak bisa. Saya sedang sibuk.";
			default -> "Sorry, I can't talk right now. I'm busy.";
		};
	}
	
	private static String detectLanguage(String text)
	{
		final String lower = (" " + text.toLowerCase(Locale.ROOT) + " ").replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ");
		if (hasAny(lower, " hola ", " que ", " donde ", " levear ", " quest ", " gracias ", " amigo ", " ayuda "))
			return "es";
		if (hasAny(lower, " oi ", " ola ", " voce ", " onde ", " obrigado ", " ajuda ", " nivel "))
			return "pt";
		if (hasAny(lower, " bonjour ", " salut ", " merci ", " quete ", " niveau ", " aide "))
			return "fr";
		if (hasAny(lower, " hallo ", " danke ", " nicht ", " gerade ", " wo ", " hilfe "))
			return "de";
		if (hasAny(lower, " ciao ", " grazie ", " dove ", " livello ", " aiuto "))
			return "it";
		if (hasAny(lower, " czesc ", " dzieki ", " gdzie ", " pomoc ", " poziom "))
			return "pl";
		if (hasAny(lower, " merhaba ", " nerede ", " tesekkur ", " yardim ", " seviye "))
			return "tr";
		if (hasAny(lower, " waar ", " dank ", " bezig ", " helpen ", " niveau "))
			return "nl";
		if (hasAny(lower, " halo ", " dimana ", " terima ", " bantu ", " sibuk "))
			return "id";
		return "en";
	}
	
	private static boolean hasAny(String text, String... tokens)
	{
		for (String token : tokens)
		{
			if (text.contains(token))
				return true;
		}
		return false;
	}
	
	private static String extractText(String response)
	{
		final Matcher matcher = TEXT_PATTERN.matcher(response);
		if (!matcher.find())
			return null;
		
		return unescapeJson(matcher.group(1));
	}
	
	private static String trimForChat(String text)
	{
		String cleaned = text.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
		if (cleaned.length() > PhantomConfig.geminiMaxOutputChars())
			cleaned = cleaned.substring(0, PhantomConfig.geminiMaxOutputChars()).trim();
		return cleaned;
	}
	
	private static String json(String value)
	{
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
	}
	
	private static String unescapeJson(String value)
	{
		return value.replace("\\n", " ").replace("\\r", " ").replace("\\\"", "\"").replace("\\\\", "\\");
	}
	
	private static String urlEncode(String value)
	{
		return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
