package org.thunlp.tagsuggest.contentbase;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.swing.Box.Filler;

import org.thunlp.misc.StringUtil;
import org.thunlp.misc.WeightString;
import org.thunlp.tagsuggest.common.FeatureExtractor;
import org.thunlp.tagsuggest.common.GenerativeTagSuggest;
import org.thunlp.tagsuggest.common.Post;
import org.thunlp.tagsuggest.common.TagSuggest;
import org.thunlp.tagsuggest.common.WordFeatureExtractor;

public class TAMTagSuggest implements TagSuggest, GenerativeTagSuggest {
	private static Logger LOG = Logger.getAnonymousLogger();
	private TagAllocationModel model;
	private FeatureExtractor extractor = new WordFeatureExtractor();
	private Properties config = null;
	private int numTags = 10;
	private int numSamples = 100;
	private boolean useNoise = false;

	@Override
	public void feedback(Post p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void loadModel(String modelPath) throws IOException {
		FileInputStream input = new FileInputStream(modelPath+File.separator +"reason");
		model = new TagAllocationModel(input);
		input.close();
		model.setLocked(true);
	}

	@Override
	public void setConfig(Properties config) {
		extractor = new WordFeatureExtractor(config);
		numTags = Integer.parseInt(config.getProperty("num_tags", "5"));
		numSamples = Integer.parseInt(config.getProperty("num_samples", "100"));
		useNoise = config.getProperty("usenoise", "false").equals("true");
		this.config = config;
	}

	private void addExplain(StringBuilder buffer, String text) {
		if (buffer != null) {
			buffer.append(text);
		}
	}

	@Override
	public List<WeightString> suggest(Post p, StringBuilder explain) {
		addExplain(explain, "<div class='explain'>");
		addExplain(explain, "<div>suggest for " + p.getContent() + "</div>");
		String[] features = extractor.extract(p);
		Set<String> featureSet = new HashSet<String>();
		for (String feature : features) {
			featureSet.add(feature);
		}
		addExplain(explain, "features: ");
		if (explain != null) {
			for (String feature : features) {
				if (model.getRelatedTags(feature).size() > 0) {
					addExplain(explain, "<span style='color:red'>" + feature
							+ "</span> ");
				} else {
					addExplain(explain, feature + " ");
				}
			}
		}
		addExplain(explain, "<br>");
		List<WeightString> results = new ArrayList<WeightString>();

		Map<String, Double> tagWeights = new Hashtable<String, Double>();
		Map<String, List<String>> tagsrc = null;
		if (explain != null) {
			tagsrc = new Hashtable<String, List<String>>();
		}
		double norm = 0;
		for (String feature : featureSet) {
			norm += model.prw(feature);
		}
		for (String feature : featureSet) {
			Set<String> ctags = model.getRelatedTags(feature);
			double prd = model.prw(feature) / norm;
			for (String tag : ctags) {
				double ptr = model.ptr(tag, feature);
				if (explain != null) {
					List<String> srcs = tagsrc.get(tag);
					if (srcs == null) {
						srcs = new LinkedList<String>();
						tagsrc.put(tag, srcs);
					}
					srcs.add(feature + ":" + String.format("%.3f", ptr));
				}
				double ptf = ptr * prd * (1 - model.pcm());
				Double w = tagWeights.get(tag);
				if (w == null) {
					w = 0.0;
				}
				tagWeights.put(tag, w + ptf);
			}
		}
		if (useNoise) {
			for (String tag : model.getRelatedTags(TagAllocationModel.NOISE)) {
				double ptr = model.ptr(tag, TagAllocationModel.NOISE);
				double ptf = ptr * model.pcm();
				Double w = tagWeights.get(tag);
				if (w == null) {
					w = 0.0;
				}
				tagWeights.put(tag, w + ptf);
			}
		}

		for (Entry<String, Double> e : tagWeights.entrySet()) {
			results.add(new WeightString(e.getKey(), e.getValue()));
		}

		addExplain(explain, "</div>");
		Collections.sort(results, new Comparator<WeightString>() {
			@Override
			public int compare(WeightString o1, WeightString o2) {
				return Double.compare(o2.weight, o1.weight);
			}
		});
		if (results.size() > numTags)
			results = results.subList(0, numTags);

		if (explain != null && results.size() > 0) {
			double largestWeight = results.get(0).weight;
			for (WeightString tag : results) {
				List<String> srcs = tagsrc.get(tag.text);
				addExplain(explain, "<div style='color:"
						+ (tag.weight < 0.2 * largestWeight ? "#AAAAAA"
								: "black") + "'>");
				if (srcs == null) {
					addExplain(explain, "(null) =&gt; " + tag + "<br>");
				} else {
					addExplain(explain, "(" + StringUtil.join(srcs, ",")
							+ ") =&gt; " + tag);
				}
				addExplain(explain, "</div>");
			}
		}
		return results;
	}

	@Override
	public void likelihood(Post p, List<Double> likelihoods) {
		String[] words = extractor.extract(p);
		for (String tag : p.getTags()) {
			likelihoods.add(model.likelihood(words, tag));
		}
	}

	public TagAllocationModel getModel() {
		return model;
	}
}
