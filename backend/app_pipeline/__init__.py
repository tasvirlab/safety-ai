"""SafetyAI backend inference pipeline — app-integration code only.

Every function in this package either calls into the validated research code
under config.research_data_root unchanged, or computes something new that
research code does not (yet) provide (the Activity Layer, the conditional
REBA wrapper, the JSON output shape). See ../README.md.
"""
